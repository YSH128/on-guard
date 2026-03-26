import torch
from torchvision.models import efficientnet_b0

# ---- 모델 설정 (모델 바꿀 때 수정) -------------
MODEL_PATH = r"best_model-v3.pt"
OUTPUT_PATH = "face_deepfake.onnx"
INPUT_SIZE_H = 224
INPUT_SIZE_W = 224
NUM_CLASSES = 2 # 0: REAL, 1: FAKE
DROPOUT = 0.4
FAKE_CLASS_IDX = 1  # fake class index
# ────────────────────────────────────────────────────────────


class FaceDetectModel_CustomOutput(torch.nn.Module):
    """
    EfficientNet_b0 wrapper.
    forward()가 (logits, cam) 튜플을 반환.

    Model input size [1, 3, 224, 224]

    Returns:
        logits: [1, 2]
            - real, fake logits
        cam: [7, 7]
            - fake class에 대한 Class Activation Map
            - classifier의 fake class weight(index=1)로 채널을 weighted sum한 결과
            - 각 셀은 원본 224x224 이미지의 32x32 픽셀 블록에 대응
            - 값이 클수록 해당 위치가 fake 판단에 기여한 영역
    """

    def __init__(self, original_model: torch.nn.Module):
        super().__init__()
        self.features = original_model.features      # [1, 1280, 7, 7]
        self.avgpool = original_model.avgpool         # [1, 1280, 1, 1]
        self.classifier = original_model.classifier  # [1, 2]
        self.fake_class_idx: int = FAKE_CLASS_IDX

    def forward(self, x: torch.Tensor):
        feat = self.features(x)                                   # [1, C, H, W]
        pooled = self.avgpool(feat)                               # [1, C, 1, 1]
        logits = self.classifier(torch.flatten(pooled, 1))        # [1, 2]

        num_channels = feat.shape[1]
        w_fake = self.classifier[1].weight[self.fake_class_idx]   # [C]
        cam = (feat.squeeze(0) * w_fake.view(num_channels, 1, 1)).sum(dim=0)  # [7, 7]
        cam = torch.relu(cam)

        return logits, cam


def convert_model_to_onnx():
    print("1. 모델 아키텍처 생성 중...")
    model = efficientnet_b0()
    num_features = model.classifier[1].in_features
    model.classifier = torch.nn.Sequential(
        torch.nn.Dropout(DROPOUT), torch.nn.Linear(num_features, NUM_CLASSES)
    )

    print("2. 학습된 가중치(state_dict) 덮어씌우는 중...")
    weights = torch.load(MODEL_PATH, map_location=torch.device("cpu"))
    model.load_state_dict(weights)
    model.eval()

    print("3. CAM wrapper 적용 중...")
    wrapped = FaceDetectModel_CustomOutput(model)
    wrapped.eval()

    print("4. output shape 확인 중...")
    example_input = torch.rand(1, 3, INPUT_SIZE_H, INPUT_SIZE_W)
    with torch.no_grad():
        logits, cam = wrapped(example_input)
    print(f"   logits shape : {logits.shape}")  # [1, 2]
    print(f"   cam    shape : {cam.shape}")      # [7, 7]

    print("5. ONNX export 중...")
    torch.onnx.export(
        wrapped,
        example_input,
        OUTPUT_PATH,
        dynamo=False,
        opset_version=17,
        input_names=["input"],
        output_names=["logits", "cam"],
        dynamic_axes={
            "input": {0: "batch_size"},
            "logits": {0: "batch_size"},
        },
    )
    print(f"성공! ONNX [{OUTPUT_PATH}] 파일이 생성되었습니다.")

    print()
    print("6. 저장된 ONNX 파일 검증 중...")
    
    import onnx
    onnx_model = onnx.load(OUTPUT_PATH)
    onnx.checker.check_model(onnx_model)
    print("   ONNX 모델 검증 통과")
    for output in onnx_model.graph.output:
        print(f"   output: {output.name} → {[d.dim_value for d in output.type.tensor_type.shape.dim]}")

    print()
    print("[Android ONNX Runtime 수신 순서]")
    print("  results[0] = logits → softmax → [0]=realProb, [1]=fakeProb")
    print("  results[1] = cam    → [7, 7] → Kotlin에서 ROI 추출")


if __name__ == "__main__":
    convert_model_to_onnx()

