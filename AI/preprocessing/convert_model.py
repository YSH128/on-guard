import torch
import torchvision
from torch.utils.mobile_optimizer import optimize_for_mobile
from torchvision.models import efficientnet_b0


def convert_to_lite():
    print("1. 모델 아키텍처(껍데기) 생성 중...")
    # Inference 코드와 완벽히 동일하게 EfficientNet 구조를 만들고 분류기를 개조합니다.
    # (모바일 변환용이므로 사전 학습된 가중치는 굳이 다운받지 않아도 됩니다.)
    model = efficientnet_b0()
    num_features = model.classifier[1].in_features
    model.classifier = torch.nn.Sequential(
        torch.nn.Dropout(0.4), torch.nn.Linear(num_features, 2)
    )

    print("2. 학습된 가중치(state_dict) 덮어씌우는 중...")
    # 지난번 경로 에러 방지를 위해 문자열 앞에 r을 붙였습니다.
    model_path = r"best_model-v3.pt"

    # 모바일(C++) 환경은 CPU 기반으로 돌아가므로 cpu로 불러옵니다.
    weights = torch.load(model_path, map_location=torch.device("cpu"))
    model.load_state_dict(weights)

    # 평가 모드 전환 (이제 껍데기가 완성되었으므로 eval()이 정상 작동합니다!)
    model.eval()

    print("3. 더미 입력 데이터 생성 및 트레이싱 중...")
    # Inference 코드의 Resize((224, 224))와 영상(3채널 RGB)에 맞춰 더미 텐서 생성
    example_input = torch.rand(1, 3, 224, 224)

    # C++에서 읽을 수 있는 TorchScript 포맷으로 변환
    traced_script_module = torch.jit.trace(model, example_input)

    # print("4. 모바일 최적화 및 저장 중...")
    # optimized_traced_model = optimize_for_mobile(traced_script_module)

    # output_path = "model_lite.ptl"
    # optimized_traced_model._save_for_lite_interpreter(output_path)

    print("4. torchscript 저장 중...")

    output_path = "vision_model.pt"
    traced_script_module.save(output_path)

    print(f"🎉 성공! TorchScript [{output_path}] 파일이 생성되었습니다.")


if __name__ == "__main__":
    convert_to_lite()
