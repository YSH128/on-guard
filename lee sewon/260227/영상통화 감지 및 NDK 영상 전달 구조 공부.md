# 0. 전체 아키텍처 요약

```plaintext
영상통화 감지
        ↓
ForegroundService 시작
        ↓
MediaProjection
        ↓
YUV Frame
        ↓
Direct ByteBuffer
        ↓
JNI
        ↓
NDK Worker Thread 추론
        ↓
결과 반환 (boolean / risk score)
        ↓
Kotlin UI/알림 처리
```


# 1. 트리거 단계 (영상통화 감지)

## 1) 통신사 영상통화 감지 조건

```plaintext
CALL_STATE_OFFHOOK
AND
Camera in use
```

조건 충족 시:

[1] ForegroundService 시작

[2] MediaProjection 활성화

---

# 2. 화면 캡처 단계 (MediaProjection)

```plaintext
MediaProjection
    ↓
VirtualDisplay 생성
    ↓
ImageReader 연결
```

* PixelFormat: RGBA_8888 또는 YUV_420_888
* HandlerThread에서 프레임 수신

---

# 3. 프레임 수신 및 전달 구조

```plaintext
ImageReader Callback (HandlerThread)
        ↓
YUV Frame 획득
        ↓
Direct ByteBuffer
        ↓
JNI 전달
```

## 핵심 포인트

[1] ByteArray 복사 금지

[2] Direct ByteBuffer 그대로 native 전달

[3] image.close() 반드시 호출

---

# 4. NDK 추론 구조

```plaintext
JNI 브리지
        ↓
NDK 내부 Worker Thread
        ↓
딥페이크 추론 실행
```

## 내부 구조

[1] 프레임을 큐에 저장

[2] Worker Thread가 프레임 pop

[3] 모델 추론 수행 (ONNX / TensorRT 등)

[4] 결과 산출 (boolean 또는 risk score)

프레임 수신 스레드와 추론 스레드는 반드시 분리한다.

---

# 5. 결과 반환

```plaintext
NDK
    ↓
JNI Callback
    ↓
Kotlin
```

반환 데이터 예시:

* boolean (딥페이크 여부)
* float riskScore (0.0 ~ 1.0)

---

# 6. UI / 알림 처리

Kotlin 측 처리:

```plaintext
IF riskScore > threshold
    → 경고 알림 표시
```

표시 방식:

[1] 일반 Notification

[2] FullScreen Intent

[3] Overlay (권한 필요)

UI 처리는 반드시 Main Thread에서 수행한다.

---

# 추가) 통신사 영상통화 및 카카오톡 페이스톡 감지 전략

# 1. 통신사 영상통화 감지

## 1) 사용 API

[1] TelephonyManager

[2] TelephonyCallback.CallStateListener

[3] CameraManager.registerAvailabilityCallback()

---

## 2) 감지 조건

```
CALL_STATE_OFFHOOK
AND
Camera in use
```

설명

* 통화 상태가 OFFHOOK
* 동시에 카메라 사용 중

위 조건을 만족하면 통신사 영상통화 가능성이 높다고 판단한다.

---

# 1. 카카오톡 페이스톡 감지

## 1) 사용 요소

[1] 포그라운드 앱 패키지 확인
packageName == "com.kakao.talk"

[2] 마이크 사용 상태 확인
audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

[3] 카메라 사용 여부 확인
CameraManager.AvailabilityCallback

---

## 2) 감지 조건

```
Foreground == com.kakao.talk
AND
Camera in use
AND
audioMode == MODE_IN_COMMUNICATION
```

설명

* 카카오톡이 포그라운드 상태
* 카메라 사용 중
* 오디오 모드가 통신 모드

---

## 3) 동영상 촬영과의 구분

* 카카오톡 동영상 촬영은 일반적으로 MODE_NORMAL
* 페이스톡은 MODE_IN_COMMUNICATION 사용

AudioMode 조건을 추가하여 영상 촬영 기능과 구분 가능하다.