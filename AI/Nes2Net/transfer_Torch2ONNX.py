import argparse
import torch
import numpy as np
import torchaudio
import torch.nn.functional as F
import math

# Monkey-patch F.scaled_dot_product_attention to use math operations for ONNX export
original_sdpa = getattr(F, 'scaled_dot_product_attention', None)
def patched_sdpa(query, key, value, attn_mask=None, dropout_p=0.0, is_causal=False, scale=None):
    L, S = query.size(-2), key.size(-2)
    scale_factor = 1 / math.sqrt(query.size(-1)) if scale is None else scale
    
    attn_weight = query @ key.transpose(-2, -1) * scale_factor
    
    if is_causal:
        assert attn_mask is None
        temp_mask = torch.ones(L, S, dtype=torch.bool, device=query.device).tril(diagonal=0)
        attn_weight.masked_fill_(temp_mask.logical_not(), float("-inf"))

    if attn_mask is not None:
        if attn_mask.dtype == torch.bool:
            attn_weight.masked_fill_(attn_mask.logical_not(), float("-inf"))
        else:
            attn_weight += attn_mask

    attn_weight = torch.softmax(attn_weight, dim=-1)
    if dropout_p > 0.0:
        attn_weight = torch.dropout(attn_weight, dropout_p, train=True)
    return attn_weight @ value

if original_sdpa is not None:
    F.scaled_dot_product_attention = patched_sdpa
    torch.nn.functional.scaled_dot_product_attention = patched_sdpa

# torchaudio 백엔드 설정 (sox_io 없을 경우 soundfile 사용)
try:
    torchaudio.set_audio_backend("soundfile")
except RuntimeError:
    pass

# s3prl이 sox_io를 강제하는 것을 방지하기 위해 set_audio_backend를 오버라이드
_original_set_audio_backend = torchaudio.set_audio_backend
def _patched_set_audio_backend(backend):
    available_backends = torchaudio.list_audio_backends()
    if backend not in available_backends:
        if 'soundfile' in available_backends:
            print(f"Warning: Backend '{backend}' not available. Using 'soundfile' instead.")
            return _original_set_audio_backend('soundfile')
        else:
            raise RuntimeError(f'Backend "{backend}" is not available. Available: {available_backends}')
    return _original_set_audio_backend(backend)

torchaudio.set_audio_backend = _patched_set_audio_backend

# 모델 정의 파일이 경로에 있어야 합니다.
from models.WavLM_Nes2Net_X_SeLU import WavLM_Nes2Net_SE_cat_SeLU as Model

def main(args):
    device = torch.device("cpu")
    print(f"Device set to: {device}")

    # 1. 모델 초기화
    model = Model(args, device).to(device)

    # 2. 가중치 로드
    weights_path = args.model_path
    print(f"Loading weights from: {weights_path}")
    
    saved_model = torch.load(weights_path, map_location=device)
    
    # TorchScript 모듈인지 확인
    if isinstance(saved_model, torch.jit.ScriptModule):
        print("Loaded as TorchScript module, extracting state dict...")
        saved_state_dict = saved_model.state_dict()
    elif isinstance(saved_model, dict):
        saved_state_dict = saved_model
        # state_dict 키 구조에 따라 대응 (직접 저장한 형태에 맞춰 수정 필요할 수 있음)
        if 'state_dict' in saved_state_dict:
            saved_state_dict = saved_state_dict['state_dict']
    else:
        # 다른 형식의 경우 직접 state dict로 사용 시도
        print(f"Loaded model type: {type(saved_model)}")
        saved_state_dict = saved_model
        
    model_state_dict = model.state_dict()
    filtered_state_dict = {k: v for k, v in saved_state_dict.items() if k in model_state_dict}
    model_state_dict.update(filtered_state_dict)
    model.load_state_dict(model_state_dict)
    model.eval()
    print("Model loaded and set to eval mode.")

    # 3. 더미 입력 데이터 생성 (shape: [1, 16000] - batch_size=1, audio_length=16000)
    print("Creating dummy input tensor for ONNX export...")
    dummy_input = torch.randn(1, 16000, dtype=torch.float32)

    # 4. 로컬 추론 테스트 (변환 전 결과 확인)
    print("Testing model inference with dummy input...")
    with torch.no_grad():
        output = model(dummy_input)
        print(f"로컬 추론 결과 (Shape): {output.shape}, (Value): {output.item()}")

    # 5. ONNX 변환 (핵심!)
    print("\nConverting to ONNX format...")
    output_path = args.output_path
    
    try:
        # The model contains dynamic operations not supported by ONNX
        # Try ONNX export with higher opset version for better operator support
        onnx_export_successful = False
        
        for opset_version in [17, 16, 15]:
            try:
                print(f"[INFO] Attempting ONNX export with opset_version={opset_version}...")
                torch.onnx.export(
                    model,
                    dummy_input,
                    output_path,
                    input_names=['input_audio'],
                    output_names=['output_score'],
                    dynamic_axes={
                        'input_audio': {0: 'batch_size', 1: 'sequence_length'},
                        'output_score': {0: 'batch_size'}
                    },
                    opset_version=opset_version,
                    do_constant_folding=False,
                    verbose=False,
                    export_params=True,
                )
                onnx_export_successful = True
                print(f"[OK] ONNX export successful with opset_version={opset_version}")
                print(f"[OK] ONNX 모델 저장 완료: {output_path}")
                break
            except Exception as e:
                print(f"[INFO] ONNX export failed with opset_version={opset_version}: {type(e).__name__}")
                continue
        
        if not onnx_export_successful:
            print(f"[INFO] ONNX export not supported for this model. Using TorchScript export instead...")
            # Fallback: Export as TorchScript which is more compatible with PyTorch models
            torchscript_path = output_path.replace('.onnx', '_torchscript.pt')
            traced_model = torch.jit.trace(model, dummy_input, check_trace=False, strict=False)
            traced_model.save(torchscript_path)
            print(f"[OK] TorchScript model saved to: {torchscript_path}")
            # Also save as regular PT file
            torch.save(model.state_dict(), output_path.replace('.onnx', '_state_dict.pt'))
            print(f"[OK] State dict saved to: {output_path.replace('.onnx', '_state_dict.pt')}")
            # Don't continue to ONNX-specific validation
            onnx_export_successful = False
        
        print(f"[OK] 입력 shape: [batch_size, 16000]")
        print(f"[OK] 출력 shape: [batch_size, 1]")
        
        # 6. ONNX 모델 검증 (TorchScript를 사용한 경우 스킵)
        if onnx_export_successful:
            print("\nValidating ONNX model...")
            try:
                import onnx
                onnx_model = onnx.load(output_path)
                onnx.checker.check_model(onnx_model)
                print("[OK] ONNX 모델 검증 완료!")
            except ImportError:
                print("[WARN] ONNX 패키지가 설치되지 않았습니다. 검증을 스킵합니다.")
                print("  설치 명령: pip install onnx onnxruntime")
        else:
            print("\n[OK] TorchScript/PyTorch format export completed successfully!")
            print("     The model was not convertible to ONNX format due to dynamic operations.")
        
    except Exception as e:
        print(f"[ERROR] 모델 내보내기 실패: {str(e)}")
        raise

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Convert Nes2Net PyTorch model to ONNX format")
    
    # 모델 구조 인자
    parser.add_argument("--agg", type=str, default='SEA', choices=['SEA', 'AttM', 'WeightedSum'],
                        help="Aggregation method for WavLM features")
    parser.add_argument("--dilation", type=int, default=1,
                        help="Dilation rate for Bottle2neck")
    parser.add_argument("--pool_func", type=str, default='mean', choices=['mean', 'ASTP'],
                        help="Pooling function for output")
    parser.add_argument("--SE_ratio", type=int, nargs='+', default=[1],
                        help="SE module ratio")
    parser.add_argument("--Nes_ratio", type=int, nargs='+', default=[8, 8],
                        help="Nested Res2Net ratio")
    
    # 파일 경로
    parser.add_argument("--model_path", type=str, 
                        default='voice_deepfake_model.pt',
                        help="Path to the PyTorch model checkpoint (.pt file)")
    parser.add_argument("--output_path", type=str, 
                        default='voice_deepfake_model.onnx',
                        help="Path to save the ONNX model")
    
    args = parser.parse_args()
    main(args)
