import os
import subprocess
import sys
import torch
from torchvision.models import efficientnet_b0

# ─── 모델 설정 (모델 바꿀 때 수정) ──────────────────
BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH    = os.path.join(BASE_DIR, "best_model-v3.pt")
ONNX_PATH     = os.path.join(BASE_DIR, "face_deepfake.onnx")   # 중간 산출물
QNN_OUT_DIR   = os.path.join(BASE_DIR, "qnn_output")           # qnn-onnx-converter 출력 디렉토리
QNN_CPP_PATH  = os.path.join(QNN_OUT_DIR, "face_deepfake.cpp") # converter가 생성하는 C++ 파일
QNN_BIN_PATH  = os.path.join(QNN_OUT_DIR, "face_deepfake.bin") # converter가 생성하는 weight bin
SO_OUT_DIR    = os.path.join(BASE_DIR, "qnn_so_output")        # .so 출력 디렉토리

INPUT_SIZE_H  = 224
INPUT_SIZE_W  = 224
NUM_CLASSES   = 2    # 0: REAL, 1: FAKE
DROPOUT       = 0.4
FAKE_CLASS_IDX = 1

# Android 타겟 아키텍처 (arm64-v8a)
TARGET_ARCH = "aarch64-android"
# ────────────────────────────────────────────────────────────


class FaceDetectModel_CustomOutput(torch.nn.Module):
    """
    EfficientNet_b0 wrapper.
    forward()가 (logits, cam) 튜플을 반환.

    Returns:
        logits : [1, 2]   real/fake logits
        cam    : [7, 7]   fake class CAM (relu 적용)
    """

    def __init__(self, original_model: torch.nn.Module):
        super().__init__()
        self.features    = original_model.features    # [1, 1280, 7, 7]
        self.avgpool     = original_model.avgpool      # [1, 1280, 1, 1]
        self.classifier  = original_model.classifier  # [1, 2]
        self.fake_class_idx: int = FAKE_CLASS_IDX

    def forward(self, x: torch.Tensor):
        feat    = self.features(x)                                    # [1, C, H, W]
        pooled  = self.avgpool(feat)                                  # [1, C, 1, 1]
        logits  = self.classifier(torch.flatten(pooled, 1))           # [1, 2]

        num_channels = feat.shape[1]
        w_fake  = self.classifier[1].weight[self.fake_class_idx]      # [C]
        cam     = (feat.squeeze(0) * w_fake.view(num_channels, 1, 1)).sum(dim=0)  # [7, 7]
        cam     = torch.relu(cam)

        return logits, cam


# ─── Step 1. ONNX export ─────────────────────────────────────────────────────
def export_onnx(wrapped: torch.nn.Module, example_input: torch.Tensor) -> bool:
    print("\n[Step 1] ONNX export 중...")
    try:
        torch.onnx.export(
            wrapped,
            example_input,
            ONNX_PATH,
            dynamo=False,
            opset_version=17,
            input_names=["input"],
            output_names=["logits", "cam"],
            dynamic_axes={
                "input":  {0: "batch_size"},
                "logits": {0: "batch_size"},
            },
        )

        import onnx
        onnx.checker.check_model(onnx.load(ONNX_PATH))
        print(f"   ONNX 저장 및 검증 완료 → {ONNX_PATH}")
        return True
    except Exception as e:
        print(f"   ONNX export 실패: {e}")
        return False


# ─── Step 2. qnn-onnx-converter ──────────────────────────────────────────────
def run_qnn_onnx_converter() -> bool:
    print("\n[Step 2] qnn-onnx-converter 실행 중...")

    os.makedirs(QNN_OUT_DIR, exist_ok=True)

    cmd = [
        "qnn-onnx-converter",
        "--input_network",    ONNX_PATH,
        "--output_path",      os.path.join(QNN_OUT_DIR, "face_deepfake"),
        "--input_dim",        "input", f"1,3,{INPUT_SIZE_H},{INPUT_SIZE_W}",
    ]

    print(f"   명령어: {' '.join(cmd)}")
    try:
        result = subprocess.run(cmd, capture_output=True, text=True)
    except FileNotFoundError:
        print("   [실패] qnn-onnx-converter 를 찾을 수 없습니다.")
        _print_qnn_sdk_guide()
        return False

    if result.returncode != 0:
        print("   [실패] qnn-onnx-converter 오류:")
        print(result.stderr)
        _print_qnn_sdk_guide()
        return False

    print("   qnn-onnx-converter 완료")
    print(f"   생성 파일: {QNN_CPP_PATH}")
    print(f"             {QNN_BIN_PATH}")
    return True


# ─── Step 3. qnn-model-lib-generator (.so 컴파일) ────────────────────────────
def run_qnn_model_lib_generator() -> bool:
    print(f"\n[Step 3] qnn-model-lib-generator 실행 중 (타겟: {TARGET_ARCH})...")

    os.makedirs(SO_OUT_DIR, exist_ok=True)

    cmd = [
        "qnn-model-lib-generator",
        "-c", QNN_CPP_PATH,
        "-b", QNN_BIN_PATH,
        "-o", SO_OUT_DIR,
        "-t", TARGET_ARCH,
    ]

    print(f"   명령어: {' '.join(cmd)}")
    try:
        result = subprocess.run(cmd, capture_output=True, text=True)
    except FileNotFoundError:
        print("   [실패] qnn-model-lib-generator 를 찾을 수 없습니다.")
        _print_qnn_sdk_guide()
        return False

    if result.returncode != 0:
        print("   [실패] qnn-model-lib-generator 오류:")
        print(result.stderr)
        _print_qnn_sdk_guide()
        return False

    so_path = os.path.join(SO_OUT_DIR, TARGET_ARCH, "libface_deepfake.so")
    if os.path.exists(so_path):
        print(f"   .so 생성 완료 → {so_path}")
    else:
        print(f"   .so 파일을 {SO_OUT_DIR} 하위에서 확인하세요.")
    return True


def _print_qnn_sdk_guide():
    print("""
  ─────────────────────────────────────────────────────────
  QNN SDK가 설치되어 있지 않거나 PATH에 없습니다.

  [QNN SDK 설치 방법]
  1. Qualcomm AI Hub 또는 Qualcomm Developer Network에서
     'Qualcomm AI Engine Direct SDK (QNN SDK)' 다운로드
     https://qpm.qualcomm.com/

  2. 설치 후 QNN SDK bin 디렉토리를 PATH에 추가:
     export PATH=$PATH:/opt/qcom/aistack/qnn/<version>/bin

  [수동 변환 명령어]
  qnn-onnx-converter \\
      --input_network face_deepfake.onnx \\
      --output_path qnn_output/face_deepfake \\
      --input_dim input "1,3,224,224"

  qnn-model-lib-generator \\
      -c qnn_output/face_deepfake.cpp \\
      -b qnn_output/face_deepfake.bin \\
      -o qnn_so_output/ \\
      -t aarch64-android
  ─────────────────────────────────────────────────────────
    """)


def _check_qnn_sdk() -> bool:
    try:
        result = subprocess.run(
            ["qnn-onnx-converter", "--version"],
            capture_output=True, text=True
        )
        return result.returncode == 0
    except FileNotFoundError:
        return False


# ─── Main ────────────────────────────────────────────────────────────────────
def convert_to_qnn():
    print("=" * 60)
    print(" QNN 모델 변환 스크립트")
    print(" 출력: logits [1,2] + cam [7,7]")
    print(" ROI 추출은 Android C++ (OpenCV)에서 수행")
    print("=" * 60)

    # 모델 준비
    print("\n[모델 준비]")
    print("  1. EfficientNet-b0 아키텍처 생성...")
    model = efficientnet_b0()
    num_features = model.classifier[1].in_features
    model.classifier = torch.nn.Sequential(
        torch.nn.Dropout(DROPOUT),
        torch.nn.Linear(num_features, NUM_CLASSES)
    )

    print("  2. 학습된 가중치 로드...")
    weights = torch.load(MODEL_PATH, map_location="cpu", weights_only=True)
    model.load_state_dict(weights)
    model.eval()

    print("  3. CAM wrapper 적용...")
    wrapped = FaceDetectModel_CustomOutput(model)
    wrapped.eval()

    example_input = torch.rand(1, 3, INPUT_SIZE_H, INPUT_SIZE_W)
    with torch.no_grad():
        logits, cam = wrapped(example_input)
    print(f"     logits : {logits.shape}")
    print(f"     cam    : {cam.shape}")

    # Step 1: ONNX export
    if not export_onnx(wrapped, example_input):
        sys.exit(1)

    # Step 2 & 3: QNN 변환 (SDK 있을 때만)
    if not _check_qnn_sdk():
        print("\n[안내] QNN SDK가 없어 ONNX까지만 변환했습니다.")
        print(f"       ONNX 파일 위치: {ONNX_PATH}")
        _print_qnn_sdk_guide()
        return

    if not run_qnn_onnx_converter():
        sys.exit(1)

    if not run_qnn_model_lib_generator():
        sys.exit(1)

    print("\n" + "=" * 60)
    print(" 변환 완료!")
    print(f"  libface_deepfake.so → {SO_OUT_DIR}/{TARGET_ARCH}/")
    print("  → app/src/main/jniLibs/arm64-v8a/ 에 복사하세요")
    print("=" * 60)
    print("""
  [주의] QnnModelLoader.cpp 수정 필요
  현재 output tensor가 1개 (logits [1,2]) 로 하드코딩되어 있습니다.
  cam [7,7] 을 두 번째 output으로 받으려면:
    - m_outputId → m_logitsId / m_camId 로 분리
    - ExecuteGraph() 시그니처에 cam 버퍼 추가
    - BuildAndCacheGraph() / LoadFromCache() tensor ID 2개 저장
    """)


if __name__ == "__main__":
    convert_to_qnn()
