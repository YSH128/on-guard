# OnGuard Demo — 딥페이크 발신자 시뮬레이터

> OnGuard 앱의 딥페이크 감지 기능을 검증하기 위한 **공격자 측 시연 도구**

---

## 1. Background

OnGuard는 영상통화 중 딥페이크를 실시간으로 감지하는 앱입니다.
감지 기능을 테스트·시연하려면 **실제로 딥페이크 영상을 송출하는 발신자** 환경이 필요합니다.

이 데모 도구는 다음 시나리오를 재현합니다.

| 상황 | 구현 방법 |
|------|-----------|
| 공격자가 다른 사람의 얼굴로 위장하여 영상통화 | 웹캠 프레임을 실시간 페이스스왑 후 Agora로 송출 |
| 수신자(피해자) 측 OnGuard 앱이 이를 탐지 | 수신된 영상에서 온디바이스 딥페이크 감지 모델 실행 |

---

## 2. Key Features & System Architecture

### 주요 기능

- **실시간 페이스스왑 송출** — 웹캠 영상에 소스 얼굴을 합성하여 Agora 채널로 스트리밍
- **Agora 영상통화 연동** — OnGuard 앱과 동일한 채널에 접속하여 실제 통화 환경 재현
- **페이스스왑 ON/OFF 전환** — 통화 중 실시간으로 스왑 활성화/비활성화 가능
- **동영상 파일 송출** — 웹캠 대신 미리 준비한 영상 파일을 소스로 사용 가능
- **카카오 로그인** — 실제 API 서버에 인증하여 통화 요청

### 전체 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                   deepfake-caller.html (브라우저)                │
│                                                                  │
│  웹캠 / 영상 파일                                                 │
│       │                                                          │
│       ▼                                                          │
│  [페이스스왑 ON?]                                                 │
│       ├── YES → WebSocket → server.py → 스왑된 프레임 반환        │
│       └── NO  → 원본 프레임 그대로                                │
│       │                                                          │
│       ▼                                                          │
│  Agora Custom Video Track → 영상통화 채널 송출                   │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ (Agora RTC)
                     ┌──────────────────────────────┐
                     │  OnGuard 앱 (수신자 Android)  │
                     │  → 딥페이크 감지 모델 실행     │
                     │  → 경고 오버레이 표시          │
                     └──────────────────────────────┘
```

### server.py 구조

```
WebSocket /ws/faceswap
  ├── TEXT { type: "set_source", image: <base64> }
  │     └── 소스 얼굴 등록 (buffalo_l 모델로 얼굴 검출)
  │         성공 → { type: "ready" }
  └── BINARY <JPEG bytes>
        └── 프레임 수신 → 얼굴 검출 → inswapper_128으로 합성 → JPEG 반환
```

### Project Structure

```
demo/
├── server.py              # FastAPI WebSocket 페이스스왑 서버
├── deepfake-caller.html   # 브라우저 기반 발신자 시연 클라이언트
├── inswapper_128.onnx     # InsightFace 페이스스왑 모델 (별도 다운로드 필요)
└── README.md
```

---

## 3. Setup

### server.py 요구 사항

| 항목 | 권장 사양 |
|------|-----------|
| Python | 3.10 ~ 3.12 |
| GPU | NVIDIA GPU 권장 (CPU도 동작, 단 지연 큼) |
| CUDA | 12.x (GPU 사용 시) |
| cuDNN | 9.x (GPU 사용 시) |

### 가상 환경 생성 및 패키지 설치

```bash
python -m venv venv
# Windows
venv\Scripts\activate
# macOS / Linux
source venv/bin/activate

pip install fastapi "uvicorn[standard]" opencv-python insightface pillow "numpy<2"
```

GPU 사용 시 추가 설치:
```bash
pip install onnxruntime-gpu
pip install nvidia-cudnn-cu12   # cuDNN DLL 자동 설치 (Windows)
```

### inswapper_128.onnx 모델 다운로드

`server.py`와 동일한 디렉토리에 위치시킵니다.

```bash
# InsightFace 공식 모델 허브에서 다운로드
# https://huggingface.co/deepinsight/inswapper/resolve/main/inswapper_128.onnx
wget https://huggingface.co/deepinsight/inswapper/resolve/main/inswapper_128.onnx
```

### deepfake-caller.html 설정

파일 내 상단 상수를 환경에 맞게 수정합니다.

```javascript
// deepfake-caller.html
const APP_ID = 'YOUR_AGORA_APP_ID';         // Agora 콘솔 App ID
const WS_URL = 'ws://localhost:8765';        // server.py 주소
```

> server.py가 원격 서버(예: Jetson)에서 실행 중이면 `WS_URL`을 해당 IP로 변경합니다.

---

## 4. Usage

### 1단계 — 페이스스왑 서버 실행

```bash
cd demo/
# GPU 사용 시 (Windows — cuDNN DLL 경로 추가)
set PATH=venv\Lib\site-packages\nvidia\cudnn\bin;%PATH%

python server.py
# → INFO: Uvicorn running on http://0.0.0.0:8765
```

### 2단계 — 브라우저에서 클라이언트 열기

`deepfake-caller.html`을 브라우저에서 직접 열거나, 로컬 서버로 서빙합니다.

```bash
# 간단한 로컬 서버 (포트 8080)
python -m http.server 8080
# → http://localhost:8080/deepfake-caller.html
```

### 3단계 — 시연 순서

```
1. [카카오 로그인] 버튼 클릭 → JWT 발급

2. 영상 소스 선택
   ├── 웹캠: 기본값 (별도 설정 불필요)
   └── 영상 파일: "파일 선택" 버튼으로 MP4 등 업로드

3. [🎭 OFF] 탭 클릭 → 페이스스왑 ON 전환
   ├── 소스 얼굴 이미지 업로드 (합성할 얼굴 사진)
   └── server.py가 실행 중이면 자동 연결 → "ready" 상태 확인

4. 피해자 카카오 ID 입력 → [통화 시작] 클릭
   └── OnGuard 앱이 설치된 수신자에게 영상통화 요청 전송

5. 수신자가 통화 수락 → 페이스스왑된 영상이 실시간으로 전달됨

6. OnGuard 앱에서 딥페이크 감지 → 경고 오버레이 표시 확인
```

### 페이스스왑 동작 확인

server.py 로그에서 다음을 확인합니다.

```
INFO:faceswap:FaceAnalysis (buffalo_l) 로드 중...
INFO:faceswap:FaceAnalysis 로드 완료
INFO:faceswap:inswapper_128 로드 중...
INFO:faceswap:모델 로드 완료
INFO:faceswap:클라이언트 연결됨
INFO:faceswap:소스 얼굴 설정 완료 (감지된 얼굴 수: 1)
```

GPU 사용 여부 확인:
```bash
python -c "import onnxruntime as ort; print(ort.get_available_providers())"
# GPU 사용: ['TensorrtExecutionProvider', 'CUDAExecutionProvider', 'CPUExecutionProvider']
# CPU만:   ['CPUExecutionProvider']
```

---

## 5. Contributors

| 이름  | 담당                                                                                                                |
|-----|-------------------------------------------------------------------------------------------------------------------|
| 박수민 | deepfake-caller.html 구현 (Agora 연동, 페이스스왑 WebSocket 클라이언트, UI), server.py 구현 (InsightFace 페이스스왑 서버, WebSocket API) |
---

> 본 도구는 OnGuard 딥페이크 탐지 기능의 **테스트 및 시연 전용**입니다.