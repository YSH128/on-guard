import torch
from torchvision.models import efficientnet_b0

# ─── 모델 설정 (모델 바꿀 때 수정) ──────────────────
MODEL_PATH = r"best_model-v3.pt"
OUTPUT_PATH = "face_deepfake_model.pt"
INPUT_SIZE_H = 224  # 입력 이미지 크기
INPUT_SIZE_W = 224
FEATURE_MAP_SIZE = 7  # backbone 마지막 feature map 크기 (efficientnet_b0 기준)
NUM_CLASSES = 2  # 0: REAL, 1: FAKE
DROPOUT = 0.4
FAKE_CLASS_IDX = 1  # fake class index
ROI_THRESHOLD = 0.5
# ────────────────────────────────────────────────────────────


#  모델 변경 시 __init__() 및 forward() 모델 구조 변경 필요
class FaceDetectModel_CustomOutput(torch.nn.Module):
    """
    EfficientNet_b0 wrapper.
    forward()가 (logits, cam, bbox_roi, bbox_score) 튜플을 반환하도록 수정.

    Model input size [1, 3, 224, 224] <- 사용하는 모델이 변경될 시, 입력 사이즈 변경이 필요한지 확인 필요

    Returns:
        logits: [1, 2]
            - real, fake probability
        cam: [7, 7]
            - fake class에 대한 Class Activation Map
            - classifier의 fake class weight(index=1)로 채널을 weighted sum한 결과
            - 각 셀은 원본 224x224 이미지의 32x32 픽셀 블록에 대응
            - 값이 클수록 해당 위치가 fake 판단에 기여한 영역
        bbox_roi: [1, 4]
            - [minx, miny, maxx, maxy] -> 입력
            - cam 값을 0 ~ 1로 정규화한 후, 일정 임계값 이상인 segmentation 중 score 평균 값이 가장 높은 bbox의 roi 값
        bbox_score: [1]
            - bbox_roi에 대한 bbox_score 값
            - 각 픽셀 fake score 합의 평균
            - 정규화하지 않은 원본 값으로 계산한 값
    """

    def __init__(self, original_model: torch.nn.Module):
        super().__init__()
        self.features = (
            original_model.features
        )  # conv backbone (출력: [1, 1280, 7, 7])
        self.avgpool = (
            original_model.avgpool
        )  # AdaptiveAvgPool2d  (출력: [1, 1280, 1, 1])
        self.classifier = (
            original_model.classifier
        )  # Dropout + Linear   (출력: [1, 2])

        self.fake_class_idx: int = FAKE_CLASS_IDX
        self.roi_threshold: float = ROI_THRESHOLD
        self.input_size_h: int = INPUT_SIZE_H
        self.input_size_w: int = INPUT_SIZE_W

    def extract_roi(
        self, score_map: torch.Tensor, threshold: float = 0.5
    ) -> tuple[torch.Tensor, torch.Tensor]:
        """
        BFS 기반 Connected Components로 최고 score ROI 추출
        torch.jit.script 호환

        Args:
            score_map: [H, W] relu 적용된 CAM 텐서
            threshold: 이진화 임계값 (default: 0.5)
        Returns:
            bbox_roi:   [1, 4]  [minx, miny, maxx, maxy] (224x224 픽셀 기준)
            bbox_score: [1]     ROI 내 정규화 CAM 평균값
        """

        H, W = score_map.shape[0], score_map.shape[1]
        SCALE_H: float = float(self.input_size_h) / float(
            H
        )  # 모델 변경 시, input 크기에 따라서 변경 필요 (224)
        SCALE_W: float = float(self.input_size_w) / float(W)

        invalid_roi = torch.tensor(
            [[-1.0, -1.0, -1.0, -1.0]], device=score_map.device
        )
        invalid_score = torch.tensor([-1.0], device=score_map.device)

        # ── 1. 정규화 ──────────────────────────────────────────────────────────
        mn = score_map.min()
        mx = score_map.max()
        if (mx - mn) < 1e-6:
            return invalid_roi, invalid_score

        norm = (score_map - mn) / (mx - mn)  # [H, W], 0~1

        # ── 2. 이진화 ──────────────────────────────────────────────────────────
        binary = norm >= threshold  # [H, W] bool

        if not binary.any():
            return invalid_roi, invalid_score

        # ── 3. BFS Connected Components ────────────────────────────────────────
        visited = torch.zeros(H, W, dtype=torch.bool, device=score_map.device)

        best_score: float = -1.0

        best_minx: float = -1.0
        best_miny: float = -1.0
        best_maxx: float = -1.0
        best_maxy: float = -1.0

        for sr in range(H):
            for sc in range(W):
                if not binary[sr, sc].item() or visited[sr, sc].item():
                    continue

                # ── BFS 시작 ───────────────────────────────────────────────────
                queue: list[tuple[int, int]] = [(sr, sc)]
                visited[sr, sc] = True

                score_sum: float = 0.0
                cnt: int = 0

                min_r: int = sr
                min_c: int = sc
                max_r: int = sr
                max_c: int = sc

                while len(queue) > 0:
                    r, c = queue.pop(0)
                    score_sum += score_map[r, c].item()
                    cnt += 1

                    if r < min_r:
                        min_r = r
                    if c < min_c:
                        min_c = c
                    if r > max_r:
                        max_r = r
                    if c > max_c:
                        max_c = c

                    # 8방향 탐색
                    for dr in [-1, 0, 1]:
                        for dc in [-1, 0, 1]:
                            if dr == 0 and dc == 0:
                                continue
                            nr = r + dr
                            nc = c + dc
                            if (
                                0 <= nr < H
                                and 0 <= nc < W
                                and not visited[nr, nc].item()
                                and binary[nr, nc].item()
                            ):
                                visited[nr, nc] = True
                                queue.append((nr, nc))

                # ── 컴포넌트 score 계산 → 최고 갱신 ──────────────────────────
                mean_score: float = score_sum / float(cnt)
                if mean_score > best_score:
                    best_score = mean_score
                    best_minx = float(min_c) * SCALE_W
                    best_miny = float(min_r) * SCALE_H
                    best_maxx = float(max_c + 1) * SCALE_W
                    best_maxy = float(max_r + 1) * SCALE_H

        if best_score < 0.0:
            return invalid_roi, invalid_score

        bbox_roi = torch.tensor(
            [[best_minx, best_miny, best_maxx, best_maxy]],
            device=score_map.device,
        )  # [1, 4]
        bbox_score = torch.tensor([best_score], device=score_map.device)  # [1]

        return bbox_roi, bbox_score

    def forward(self, x: torch.Tensor):
        feat = self.features(x)  # [1, C, H, W]
        pooled = self.avgpool(feat)  # [1, C, 1, 1]
        logits = self.classifier(torch.flatten(pooled, 1))  # [1, NUM_CLASSES]

        num_channels = feat.shape[1]  # C (모델마다 다름)
        w_fake = self.classifier[1].weight[self.fake_class_idx]  # [C]
        cam = (feat.squeeze(0) * w_fake.view(num_channels, 1, 1)).sum(
            dim=0
        )  # [7, 7]
        cam = torch.relu(cam)  # fake에 반하는 방향(음수) 제거
        bbox_roi, roi_score = self.extract_roi(
            score_map=cam, threshold=self.roi_threshold
        )

        return logits, cam, bbox_roi, roi_score


def convert_to_torchscript():
    print("1. 모델 아키텍처 생성 중...")
    # Model이 바뀔 경우, 해당 모델 아키텍처로 변경 필요
    # 현재 모델은 backbone efficientent_b0 네트워크 이용
    model = efficientnet_b0()
    num_features = model.classifier[1].in_features
    model.classifier = torch.nn.Sequential(
        torch.nn.Dropout(DROPOUT), torch.nn.Linear(num_features, NUM_CLASSES)
    )

    print("2. 학습된 가중치(state_dict) 덮어씌우는 중...")
    weights = torch.load(MODEL_PATH, map_location=torch.device("cpu"))
    model.load_state_dict(weights)
    model.eval()

    print("3. ROI wrapper 적용 중...")
    wrapped = FaceDetectModel_CustomOutput(model)
    wrapped.eval()

    print("4. dummy input data  생성 및 트레이싱 중...")
    example_input = torch.rand(1, 3, INPUT_SIZE_H, INPUT_SIZE_W)
    scripted = torch.jit.script(wrapped)

    # output shape 확인
    with torch.no_grad():
        logits, cam, bbox_roi, roi_score = scripted(example_input)
    print(f"   logits    shape : {logits.shape}")  # [1, 2]
    print(
        f"   cam       shape : {cam.shape}"
    )  # [7, 7] <- 네트워크 구조에 따라 바뀔 수 있음
    print(f"   bbox_roi  shape : {bbox_roi.shape}")
    print(f"   roi_score shape : {roi_score.shape}")

    print("5. TorchScript 저장 중...")
    output_path = "face_deepfake_model.pt"
    scripted.save(output_path)
    print(f"성공! TorchScript [{output_path}] 파일이 생성되었습니다.")

    print()
    print("6. 저장된 파일 재로드 후 tuple 구조 검증 중...")
    loaded = torch.jit.load(output_path)
    loaded.eval()
    with torch.no_grad():
        out = loaded(example_input)

    if isinstance(out, tuple):
        print(f"   tuple 원소 수: {len(out)}  ← C++에서 'tuple size: {len(out)}' 으로 보임")
        for i, elem in enumerate(out):
            if isinstance(elem, torch.Tensor):
                print(f"   [{i}] Tensor  shape={list(elem.shape)}  dtype={elem.dtype}")
            else:
                print(f"   [{i}] 비Tensor 타입: {type(elem)}")
    else:
        print(f"   출력이 tuple이 아님: {type(out)}")

    print()
    print("[C++ tuple 수신 순서]")
    print("  elements[0] = logits   → softmax → prob[0][0]=realProb, prob[0][1]=fakeProb")
    print("  elements[1] = cam      → [7, 7]")
    print("  elements[2] = bbox_roi → [1, 4]  [minx, miny, maxx, maxy]")
    print("  elements[3] = roi_score → [1]")


if __name__ == "__main__":
    convert_to_torchscript()
