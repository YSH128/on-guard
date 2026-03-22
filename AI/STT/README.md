# STT (Speech-to-Text) 모듈 환경 설정 가이드

본 문서는 사내/사설 Jetson(Jetpack 6.2) 에지 환경에서 `sherpa-onnx`를 이용한 STT(한국어) 및 GMS(의도 분류/요약) 기능을 올바르게 구동하기 위한 설치 및 세팅 방법을 안내합니다.

## 1. PyTorch 설치 (Jetson 전용)

Jetson 보드의 환경(Jetpack 6.2)에 맞춰 사전 빌드된 PyTorch 휠 패키지를 설치해야 합니다.

```bash
# PyTorch 다운로드 링크 설정
export TORCH_INSTALL=https://developer.download.nvidia.com/compute/redist/jp/v61/pytorch/torch-2.5.0a0+872d972e41.nv24.08.17622132-cp310-cp310-linux_aarch64.whl

# pip 업그레이드 및 PyTorch 패키지 설치
python3 -m pip install --upgrade pip
python3 -m pip install --no-cache $TORCH_INSTALL
```

## 2. CUDA 설정 및 연동

GPU 연산을 지원하기 위해 CUDA Keyring 및 `libcusparselt` 패키지를 설치합니다.

```bash
wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2204/arm64/cuda-keyring_1.1-1_all.deb
sudo dpkg -i cuda-keyring_1.1-1_all.deb
sudo apt-get update
sudo apt-get install -y libcusparselt0 libcusparselt-dev

# CUDA 경로 환경 변수 추가 (.bashrc에 추가 권장)
export LD_LIBRARY_PATH=/usr/local/cuda/lib64:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH=/usr/lib/aarch64-linux-gnu:$LD_LIBRARY_PATH

# 정상적으로 CUDA 인식이 되는지 확인 (True 출력 예상)
python -c "import torch; print(torch.cuda.is_available())"
```

## 3. Torchaudio 소스 빌드 (Release 2.5)

PyTorch 버전에 호환되는 `torchaudio`를 공식 문서를 참고하여 소스에서 직접 빌드, 설치합니다. (https://docs.pytorch.org/audio/stable/build.jetson.html) 

```bash
# 기본 CUDA 환경 변수 세팅
export CUDA_HOME=/usr/local/cuda
export PATH=/usr/local/cuda/bin:$PATH
export LD_LIBRARY_PATH=/usr/local/cuda/lib64:$LD_LIBRARY_PATH

# (Torchaudio 레포지토리가 클론되어있다고 가정)
git checkout release/2.5
pip install wheel setuptools

# Torchaudio 설치 진행
CUDA_HOME=/usr/local/cuda USE_CUDA=1 pip install -v . --no-build-isolation
```

## 4. Sherpa-ONNX 한국어 모델 다운로드

한국어 음성 인식을 지원하는 `zipformer` (24년 6월 24일 버전) 모델 디렉터리를 다운받아 압축을 풀어줍니다.

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-korean-2024-06-24.tar.bz2

# 압축 해제 및 원본 파일 삭제
tar xf sherpa-onnx-zipformer-korean-2024-06-24.tar.bz2
rm sherpa-onnx-zipformer-korean-2024-06-24.tar.bz2
```

## 5. 나머지 패키지 설치

`torch`와 `torchaudio`가 세팅되었으므로 FastAPI, Uvicorn, Dotenv 등 서버 구동에 필요한 나머지 라이브러리를 설치합니다.

```bash
pip install -r requirements.txt
```

## 6. GMS API 키 (.env 구성)

해당 서버는 의도를 분류하기 위해 외부의 GMS `gpt-5-mini` 모델과 통신합니다. STT/ 폴더의 루트 위치에 `.env` 파일을 만들고 아래와 같이 작성하세요.

```env
GMS_API_TOKEN=$KEY
```

## 서버 구동 테스트 방법

- 간단 추론 테스트 스크립트 실행
  ```bash
  python test_sherpa.py
  ```
- 메인 웹소켓 백엔드 애플리케이션 실행
  ```bash
  uvicorn sherpa_app:app --host 0.0.0.0 --port 8000
  ```

## Reference
[torch 설치](https://docs.nvidia.com/deeplearning/frameworks/install-pytorch-jetson-platform/index.html)
[torchaudio 설치](https://docs.pytorch.org/audio/stable/build.jetson.html)
[sherpa-onnx 모델 다운로드](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html#sherpa-onnx-zipformer-korean-2024-06-24-korean)
[sherpa-onnx Repo](https://github.com/k2-fsa/sherpa-onnx?tab=readme-ov-file)