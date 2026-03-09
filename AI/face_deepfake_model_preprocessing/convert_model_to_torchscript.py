import torch
from torchvision.models import efficientnet_b0


class EfficientNetROI(torch.nn.Module):
    """
    EfficientNet_b0 래퍼.
    forward()가 (logits, cam) 튜플을 반환하도록 수정.

    cam: [7, 7] — fake class에 대한 Class Activation Map
      - classifier의 fake class weight(index=1)로 채널을 weighted sum한 결과
      - 각 셀은 원본 224x224 이미지의 32x32 픽셀 블록에 대응
      - 값이 클수록 해당 위치가 fake 판단에 기여한 영역
    """

    def __init__(self, original_model: torch.nn.Module):
        super().__init__()
        self.features   = original_model.features    # conv backbone (출력: [1, 1280, 7, 7])
        self.avgpool    = original_model.avgpool     # AdaptiveAvgPool2d  (출력: [1, 1280, 1, 1])
        self.classifier = original_model.classifier  # Dropout + Linear   (출력: [1, 2])

    def forward(self, x: torch.Tensor):
        feat   = self.features(x)                            # [1, 1280, 7, 7]
        pooled = self.avgpool(feat)                          # [1, 1280, 1, 1]
        logits = self.classifier(torch.flatten(pooled, 1))   # [1, 2]

        # CAM for fake class (index=1)
        # classifier[1] = Linear(1280, 2), weight shape: [2, 1280]
        w_fake = self.classifier[1].weight[1]                # [1280]
        cam = (feat.squeeze(0) * w_fake.view(1280, 1, 1)).sum(dim=0)  # [7, 7]
        cam = torch.relu(cam)   # fake에 반하는 방향(음수) 제거

        return logits, cam   # cam: [7, 7], C++에서 threshold/bbox는 별도 처리


def convert_to_roi():
    print("1. 모델 아키텍처(껍데기) 생성 중...")
    model = efficientnet_b0()
    num_features = model.classifier[1].in_features
    model.classifier = torch.nn.Sequential(
        torch.nn.Dropout(0.4), torch.nn.Linear(num_features, 2)
    )

    print("2. 학습된 가중치(state_dict) 덮어씌우는 중...")
    model_path = r"best_model-v3.pt"
    weights = torch.load(model_path, map_location=torch.device("cpu"))
    model.load_state_dict(weights)
    model.eval()

    print("3. ROI 래퍼 적용 중...")
    wrapped = EfficientNetROI(model)
    wrapped.eval()

    print("4. 더미 입력 데이터 생성 및 트레이싱 중...")
    example_input = torch.rand(1, 3, 224, 224)
    traced = torch.jit.trace(wrapped, example_input)

    # 출력 shape 확인
    with torch.no_grad():
        logits, cam = traced(example_input)
    print(f"   logits shape : {logits.shape}")   # [1, 2]
    print(f"   cam    shape : {cam.shape}")      # [7, 7]

    print("5. TorchScript 저장 중...")
    output_path = "vision_model_roi.pt"
    traced.save(output_path)
    print(f"성공! TorchScript [{output_path}] 파일이 생성되었습니다.")
    print()
    print("[C++ tuple 수신 순서]")
    print("  elements[0] = logits  → softmax → prob[0][0]=realProb, prob[0][1]=fakeProb")
    print("  elements[1] = cam [7,7]  → fake class activation map (threshold/bbox는 C++에서 처리)")


if __name__ == "__main__":
    convert_to_roi()
