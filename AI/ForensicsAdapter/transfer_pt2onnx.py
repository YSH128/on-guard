import torch
from model.ds import DS  # 실제 프로젝트 경로에 맞게 임포트

# ---- 모델 설정 (모델 바꿀 때 수정) -------------
MODEL_PATH = "weights/ckpt_best.pth"
OUTPUT_PATH = "./ds_model.onnx"
INPUT_SIZE_H = 256
INPUT_SIZE_W = 256
# ────────────────────────────────────────────────────────────

class DSModel_ONNXWrapper(torch.nn.Module):
    """
    DS 모델의 ONNX Export를 위한 Wrapper 클래스.
    forward()가 (logits, cam) 튜플을 반환합니다.

    Model input size: [B, 3, 224, 224]

    Returns:
        logits: [B, 2]
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
        cam = xray_preds[-1]             # [B, 1, 224, 224] 형태의 CAM(xray_pred) 리턴
        
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
            opset_version=17,
            input_names=["input"],
            output_names=["logits", "cam"]
        )
    finally:
        torch.Tensor.uniform_ = _old_uniform
        F.dropout = _old_dropout
        F.dropout2d = _old_dropout2d
        
    print(f"ONNX 추출 완료 -> {OUTPUT_PATH}")

if __name__ == "__main__":
    convert_ds_to_onnx()
