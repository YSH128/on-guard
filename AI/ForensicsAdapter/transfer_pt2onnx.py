from onnxconverter_common import float16
import onnx
import torch
from model.ds import DS  # 실제 프로젝트 경로에 맞게 임포트
import numpy as np
from pathlib import Path

# ---- 모델 설정 (모델 바꿀 때 수정) -------------
MODEL_PATH = "weights/ckpt_best.pth"
OUTPUT_PATH = "./ds_model.onnx"
OUTPUT_PATH_QUANTIZED = "./ds_model_quantized.onnx"
INPUT_SIZE_H = 256
INPUT_SIZE_W = 256
QUANTIZATION_TYPE = "float16"  # "dynamic" 또는 "static", "float16"
# ────────────────────────────────────────────────────────────

class DSModel_ONNXWrapper(torch.nn.Module):
    """
    DS 모델의 ONNX Export를 위한 Wrapper 클래스.
    forward()가 (logits, cam) 튜플을 반환합니다.

    Model input size: [B, 3, 224, 224]

    Returns:
 OB       logits: [B, 2]
            - real, fake logits (또는 probabilities)
        cam: [B, 1, 224, 224]
            - xray_pred (fake 탐지에 영향을 준 픽셀 단위 맵 역할)
    """
    def __init__(self, original_ds_model: torch.nn.Module):
        super().__init__()
        self.model = original_ds_model

    def forward(self, image: torch.Tensor):
        B = image.shape[0]
        
        # DS 모델의 forward는 내부 구조상 data_dict['if_boundary']를 요구합니다.
        # MaskPostXrayProcess 등에서 곱해지는 값이며, inference 시엔 1(ones) 배열로 넣어줍니다.
        if_boundary = torch.ones(B, 256, device=image.device)
        
        data_dict = {
            'image': image,
            'if_boundary': if_boundary
        }
        
        # 1. 모델 내부 로직 수행 (`inference=True/False` 의존성을 우회하거나, 
        # Metric 누적 코드인 `.cpu().numpy()`가 없는 상태를 가정해야 합니다)
        # 만약 ds.py를 수정할 수 없다면 아래처럼 모델 연산만 수동 호출할 수 있습니다.
        
        clip_images = torch.nn.functional.interpolate(
            image, size=(224, 224), mode='bilinear', align_corners=False
        )
        clip_features = self.model.clip_model.extract_features(clip_images, self.model.adapter.fusion_map.values())
        attn_biases, xray_preds, _ = self.model.adapter(data_dict, clip_features, inference=True)
        clip_output, _ = self.model.rec_attn_clip(data_dict, clip_features, attn_biases[-1], inference=True, normalize=True)
        
        xray_preds = [self.model.masked_xray_post_process(xp, data_dict['if_boundary']) for xp in xray_preds]
        clip_cls_output = self.model.clip_post_process(clip_output.float()).view(-1, 2)
        
        logits = clip_cls_output         # [B, 2] 형태의 Logit 리턴
        # cam = xray_preds[-1]             # [B, 1, 224, 224] 형태의 CAM(xray_pred) 리턴
        
        # CAM 이용 ROI 추출 시, 노이즈의 영향을 줄이기 위해서 각 픽셀의 값을 0~1 사이의 확률값으로 변환
        cam = torch.sigmoid(xray_preds[-1])

        return logits, cam


def convert_ds_to_onnx():
    print("1. 모델 아키텍처 생성 중...")
    import yaml
    with open('config/valid_data_test.yaml', 'r') as f:
        config = yaml.safe_load(f)

    # 파라미터는 yaml 값을 기반으로 채워주세요. (단, device='cpu' 권장)
    device = torch.device('cpu') 
    ds_model = DS(
        clip_name=config['clip_model_name'],
        adapter_vit_name=config['vit_name'],
        num_quires=config['num_quires'],
        fusion_map=config['fusion_map'],
        mlp_dim=config['mlp_dim'],
        mlp_out_dim=config['mlp_out_dim'],
        head_num=config['head_num'],
        device=device
    )

    print("2. 가중치 로드 중...")
    try:
        ckpt = torch.load(MODEL_PATH, map_location=device, weights_only=True)
    except Exception:
        # 가중치 외 객체가 포함되어 있으면 예전 방식(False)으로 fallback하여 경고만 억제
        ckpt = torch.load(MODEL_PATH, map_location=device, weights_only=False)
    ds_model.load_state_dict(ckpt, strict=False)
    ds_model.eval()

    print("3. ONNX Wrapper 적용 중...")
    wrapped_model = DSModel_ONNXWrapper(ds_model)
    wrapped_model.eval()

    print("3.5 ONNX Export를 위해 모든 Dropout 및 DropPath 구조적 제거 중...")
    def replace_stochastic_layers(module):
        for name, child in module.named_children():
            if isinstance(child, torch.nn.Dropout) or type(child).__name__ == 'DropPath':
                setattr(module, name, torch.nn.Identity())
            else:
                replace_stochastic_layers(child)
                
    replace_stochastic_layers(wrapped_model)
    
    print("4. ONNX Export 진행 중 (PyTorch 2.4 최적화 TorchScript 추적)...")
    example_input = torch.rand(1, 3, INPUT_SIZE_H, INPUT_SIZE_W)
    
    # 파이토치의 텐서 내부 난수 생성기를 완벽히 틀어막습니다. (aten::uniform 방어)
    import torch.nn.functional as F
    _old_uniform = torch.Tensor.uniform_
    _old_dropout = F.dropout
    _old_dropout2d = F.dropout2d
    torch.Tensor.uniform_ = lambda self, *args, **kwargs: self.fill_(0.0)
    F.dropout = lambda input, p=0.5, training=True, inplace=False: input
    F.dropout2d = lambda input, p=0.5, training=True, inplace=False: input

    try:
        torch.onnx.export(
            wrapped_model,
            example_input,
            OUTPUT_PATH,
            export_params=True,
            opset_version=15,
            do_constant_folding=True,
            input_names=["input"],
            output_names=["logits", "cam"]
        )
    finally:
        torch.Tensor.uniform_ = _old_uniform
        F.dropout = _old_dropout
        F.dropout2d = _old_dropout2d
        
    print(f"✓ ONNX 추출 완료 -> {OUTPUT_PATH}")


def quantize_onnx_dynamic(onnx_model_path, output_path):
    """
    [권장] ONNX 모델을 Dynamic Quantization으로 변환
    - 추가 데이터 불필요
    - 성능 손실 최소 (~1% 이하)
    - 파일 크기: ~30-40% 감소
    """
    print("\n" + "="*60)
    print("양자화: Dynamic INT8 Quantization 진행 중...")
    print("="*60)
    
    try:
        from onnxruntime.transformers import optimizer
        from onnxruntime.quantization import quantize_dynamic, QuantType
        
        # Dynamic quantization 적용 (INT8)
        quantize_dynamic(
            model_input=onnx_model_path,
            model_output=output_path,
            optimize_model=True,  # 모델 최적화도 함께 수행
            per_channel=False,     # False = 더 빠른 추론, True = 약간 높은 정확도
            reduce_type=QuantType.QUInt8,
            weight_type=QuantType.QUInt8
        )
        print(f"✓ Dynamic 양자화 완료: {output_path}")
        print_model_size(onnx_model_path, output_path)
        
    except ImportError:
        print("⚠️  onnxruntime이 설치되어 있지 않습니다.")
        print("설치: pip install onnx onnxruntime")
        return False
    
    return True


def quantize_onnx_static(onnx_model_path, output_path, calibration_data_dir=None):
    """
    [고급] ONNX 모델을 Static Quantization으로 변환
    - 캘리브레이션 데이터로 정확한 범위 계산
    - 성능 손실 거의 없음
    - 추론 속도: Dynamic보다 빠름
    """
    print("\n" + "="*60)
    print("양자화: Static INT8 Quantization 진행 중...")
    print("="*60)
    
    if calibration_data_dir is None:
        print("⚠️  Static 양자화는 캘리브레이션 데이터가 필요합니다.")
        print("   valid_data/ 디렉토리의 이미지를 활용해주세요.")
        return False
    
    try:
        from onnxruntime.quantization import quantize_static, QuantFormat, QuantType
        from onnxruntime.quantization.calibrate import create_calibrator
        import glob
        import cv2
        
        # 캘리브레이션 데이터 수집
        image_files = glob.glob(f"{calibration_data_dir}/**/*.jpg", recursive=True)[:100]
        if not image_files:
            print(f"❌ 캘리브레이션 이미지를 찾을 수 없습니다: {calibration_data_dir}")
            return False
        
        print(f"캘리브레이션 이미지: {len(image_files)}개")
        
        def calibration_data_generator():
            """캘리브레이션 데이터 생성기"""
            for img_path in image_files:
                try:
                    img = cv2.imread(img_path)
                    if img is None:
                        continue
                    img = cv2.resize(img, (INPUT_SIZE_W, INPUT_SIZE_H))
                    # 정규화
                    img = img.astype(np.float32) / 255.0
                    img = np.transpose(img, (2, 0, 1))  # HWC -> CHW
                    yield {"input": np.expand_dims(img, 0)}  # Batch 차원 추가
                except Exception as e:
                    print(f"⚠️  이미지 로드 실패: {img_path} - {e}")
                    continue
        
        # Static quantization 적용
        quantize_static(
            model_input=onnx_model_path,
            model_output=output_path,
            calibration_data_reader=calibration_data_generator(),
            quant_format=QuantFormat.QDQ,  # QDQ (Quantize-DeQuantize) 포맷 사용
            per_channel=True,
            reduce_type=QuantType.QUInt8,
            weight_type=QuantType.QUInt8
        )
        print(f"✓ Static 양자화 완료: {output_path}")
        print_model_size(onnx_model_path, output_path)
        
    except ImportError:
        print("⚠️  onnxruntime이 설치되어 있지 않습니다.")
        print("설치: pip install onnx onnxruntime")
        return False
    
    return True


def quantize_onnx_float16(onnx_model_path, output_path):
    """
    ONNX 모델을 Float16으로 변환
    - 입출력은 float32 유지 (keep_io_types=True) → Android C++ 코드 변경 불필요
    - 내부 가중치/연산만 float16으로 변환
    - 파일 크기: ~50% 감소
    """
    print("\n" + "="*60)
    print("양자화: Float16 변환 진행 중...")
    print("="*60)

    try:
        model = onnx.load(onnx_model_path)
        # keep_io_types=True: 입출력 타입을 float32로 유지, 내부만 float16
        model_fp16 = float16.convert_float_to_float16(model, keep_io_types=True)
        onnx.save(model_fp16, output_path)

        print(f"✓ Float16 변환 완료: {output_path}")
        print_model_size(onnx_model_path, output_path)

    except Exception as e:
        print(f"❌ Float16 변환 실패: {e}")
        print("필요한 라이브러리: pip install onnx onnxconverter-common")
        return False

    return True


def print_model_size(original_path, quantized_path):
    """모델 크기 비교"""
    try:
        orig_size = Path(original_path).stat().st_size / (1024 * 1024)  # MB
        quant_size = Path(quantized_path).stat().st_size / (1024 * 1024)  # MB
        reduction = (1 - quant_size / orig_size) * 100
        
        print(f"\n📊 모델 크기 비교:")
        print(f"   원본:      {orig_size:.2f} MB")
        print(f"   양자화:    {quant_size:.2f} MB")
        print(f"   감소율:    {reduction:.1f}%")
    except Exception as e:
        print(f"⚠️  파일 크기 계산 실패: {e}")


if __name__ == "__main__":
    convert_ds_to_onnx()
    
    # ============ 양자화 (선택사항) ============
    print("\n" + "="*60)
    print("ONNX 모델 양자화")
    print("="*60)
    print(f"양자화 타입: {QUANTIZATION_TYPE}")
    print("\n💡 양자화 옵션:")
    print("   - 'dynamic'  : Dynamic INT8 (권장, 간단함, 성능손실 1% 이하)")
    print("   - 'static'   : Static INT8 (캘리브레이션 필요, 성능 최고)")
    print("   - 'float16'  : Float16 (성능손실 거의 없음, 파일 50% 감소)")
    
    if QUANTIZATION_TYPE == "dynamic":
        quantize_onnx_dynamic(OUTPUT_PATH, OUTPUT_PATH_QUANTIZED)
    elif QUANTIZATION_TYPE == "static":
        # valid_data 디렉토리에 실제 deepfake 탐지 데이터가 있다고 가정
        success = quantize_onnx_static(OUTPUT_PATH, OUTPUT_PATH_QUANTIZED, 
                                       calibration_data_dir="valid_data")
        if not success:
            print("\n💡 Tip: valid_data 디렉토리에 이미지를 추가하고 다시 실행하세요.")
            print("   또는 QUANTIZATION_TYPE을 'dynamic'으로 변경하세요.")
    elif QUANTIZATION_TYPE == "float16":
        quantize_onnx_float16(OUTPUT_PATH, OUTPUT_PATH_QUANTIZED)
    else:
        print(f"❌ 지원하지 않는 양자화 타입: {QUANTIZATION_TYPE}")
    
    print("\n" + "="*60)
    print("✓ 변환 완료!")
    print("="*60)
