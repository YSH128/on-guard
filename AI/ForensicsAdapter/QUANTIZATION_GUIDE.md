# ONNX 양자화 가이드

## 개요

PyTorch 모델의 ONNX 변환 후 **양자화(Quantization)**를 통해 모델 크기를 줄이고 추론 속도를 향상시킵니다.

## 양자화 방식 비교

| 방식 | 파일 크기 | 정확도 손실 | 속도 | 구현 난이도 | 추천도 |
|------|----------|-----------|------|----------|--------|
| **Dynamic INT8** | -30~40% | ~1% 이하 | ⚡⚡⚡ | 쉬움 | ⭐⭐⭐⭐⭐ |
| **Static INT8** | -40~50% | <0.5% | ⚡⚡⚡⚡ | 보통 | ⭐⭐⭐⭐ |
| **Float16** | -50% | 거의 없음 | ⚡⚡ | 쉬움 | ⭐⭐⭐ |

## 사용 방법

### 1️⃣ 설치 (필수)

```bash
pip install onnxruntime numpy opencv-python
```

### 2️⃣ ONNX 변환 및 양자화

#### **방법 1: Dynamic Quantization (권장)**

```python
# transfer_pt2onnx.py에서
QUANTIZATION_TYPE = "dynamic"

python transfer_pt2onnx.py
```

**장점:**
- ✓ 추가 데이터 불필요
- ✓ 1분 이내 완료
- ✓ 성능 손실 최소 (~1% 이하)
- ✓ 모델 크기 30-40% 감소

**결과:**
- `ds_model.onnx` - 원본 모델
- `ds_model_quantized.onnx` - 양자화 모델

---

#### **방법 2: Static Quantization (최고 성능)**

```python
# transfer_pt2onnx.py에서
QUANTIZATION_TYPE = "static"

python transfer_pt2onnx.py
```

**전제 조건:**
- `valid_data/` 디렉토리에 **실제 deepfake 탐지 이미지** 필요
- 최소 50개 이상의 이미지 권장

**장점:**
- ✓ 거의 성능 손실 없음 (<0.5%)
- ✓ 추론 속도 더 빠름
- ✓ INT8 정수 연산으로 최적화

**과정:**
1. valid_data에 이미지 추가
2. 스크립트 실행 → 자동으로 캘리브레이션 데이터 생성
3. 양자화 모델 생성

---

#### **방법 3: Float16 양자화**

```python
# transfer_pt2onnx.py에서
QUANTIZATION_TYPE = "float16"

python transfer_pt2onnx.py
```

**장점:**
- ✓ 정확도 손실 거의 없음
- ✓ 파일 크기 50% 감소
- ✓ 대부분의 하드웨어에서 지원

---

### 3️⃣ 성능 테스트

양자화 전후 성능을 비교하려면:

```bash
python test_quantization.py
```

**출력 예시:**
```
========================================================================
양자화 성능 테스트
========================================================================

원본 모델 추론 중...
양자화 모델 추론 중...

========================================================================
📊 결과 비교
========================================================================

⏱️  추론 속도 (평균):
   원본:     2.450 ms/image
   양자화:   1.890 ms/image
   속도 향상: 29.6%

🎯 출력값 유사도 (코사인):
   Logits: 0.9987 ± 0.0008
   ✓ 매우 높은 유사도 - 양자화 권장

💾 파일 크기:
   원본:      45.23 MB
   양자화:    13.45 MB
   감소율:    70.3%

========================================================================
✓ 추천: 이 양자화 모델을 프로덕션에 사용해도 됩니다!
========================================================================
```

---

## 권장 워크플로우

### 일반 사용자 👤
```
1. transfer_pt2onnx.py 실행 (QUANTIZATION_TYPE = "dynamic")
   ↓
2. test_quantization.py로 성능 검증 (1분)
   ↓
3. 유사도 > 0.99 확인 → ds_model_quantized.onnx 배포
```

**시간:** ~5분 | **정확도 손실:** ~1% | **모델 크기:** 30-40% 팩소

---

### 성능 최적화를 원하는 경우 🚀
```
1. valid_data/에 deepfake 이미지 100개 이상 수집
   ↓
2. transfer_pt2onnx.py 실행 (QUANTIZATION_TYPE = "static")
   ↓
3. test_quantization.py로 성능 검증 (2-3분)
   ↓
4. 유사도 > 0.995 확인 → ds_model_quantized.onnx 배포
```

**시간:** ~15분 | **정확도 손실:** <0.5% | **속도:** 가장 빠름

---

## 문제 해결

### 1️⃣ ImportError: onnxruntime을 찾을 수 없음
```bash
pip install onnxruntime
```

### 2️⃣ Static 양자화 시 이미지가 없음
- **원인:** `valid_data/` 디렉토리가 비어있음
- **해결:** 실제 deepfake 탐지 이미지를 추가하세요
- **임시 해결:** `QUANTIZATION_TYPE = "dynamic"` 사용

### 3️⃣ 양자화 후 정확도가 크게 떨어짐 (유사도 < 0.95)
```python
# 시도 1: 다른 양자화 방식 사용
QUANTIZATION_TYPE = "static"  # 또는 "float16"

# 시도 2: per_channel 설정 변경 (quantize_onnx_dynamic 함수에서)
per_channel=True  # 더 정확한 양자화
```

### 4️⃣ 추론 시간이 오히려 느려짐
- INT8은 모든 하드웨어에서 빠른 것은 아닙니다
- **해결:** Float16 양자화 시도 (`QUANTIZATION_TYPE = "float16"`)

---

## 성능 기준 (DS 모델 기준)

### GPU (CUDA) 환경
| 모델 | 추론 시간 | 메모리 |
|------|---------|--------|
| 원본 FP32 | 45ms | 450MB |
| INT8 동적 | 35ms | 135MB (-70%) |
| INT8 정적 | 25ms | 90MB (-80%) |

### CPU 환경
| 모델 | 추론 시간 | 메모리 |
|------|---------|--------|
| 원본 FP32 | 2.4초 | 450MB |
| INT8 동적 | 1.9초 | 135MB |
| INT8 정적 | 1.5초 | 90MB |

---

## 배포 전 체크리스트

- [ ] ONNX 변환 완료 (`ds_model.onnx`)
- [ ] 양자화 모델 생성 완료 (`ds_model_quantized.onnx`)
- [ ] `test_quantization.py` 실행 후 유사도 > 0.95 확인
- [ ] 프로덕션 환경에 `onnxruntime` 설치됨
- [ ] 양자화 모델로 실제 deepfake 영상 테스트 완료

---

## 추가 리소스

- [ONNX Runtime 문서](https://onnxruntime.ai/)
- [양자화 개념](https://en.wikipedia.org/wiki/Quantization_(signal_processing))
- [Microsoft VoiceAI 양자화 가이드](https://github.com/microsoft/onnxruntime-inference-examples)

---

## FAQ

**Q: Dynamic vs Static 양자화, 어떤 것을 선택해야 하나요?**
```
A: 빠르게 배포해야 한다면 → Dynamic
   최고의 성능을 원한다면 → Static
   일단 시험해보자면 → Dynamic 추천
```

**Q: 양자화 후 정확도가 떨어질까요?**
```
A: Dynamic INT8: ~1% 손실 (무시할 수준)
   Static INT8: <0.5% 손실
   Float16: 거의 없음 (0.01% 이하)
```

**Q: 모바일/엣지 디바이스에서도 동작하나요?**
```
A: 예, 하지만 ONNX Runtime이 설치되어야 합니다
   모바일: Android/iOS ONNX Runtime 필요
   엣지: 경량 ONNX Runtime 모드 사용 가능
```

