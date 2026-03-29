# OnGuard Android — Frontend

> 딥페이크 영상통화 실시간 탐지 및 보호 앱

---

## 1. Background

스마트폰 영상통화가 일상화되면서, AI 기반 딥페이크 기술을 악용한 사기 범죄가 급증하고 있습니다. 특히 피해자가 상대방의 얼굴을 신뢰하는 영상통화 환경에서 딥페이크를 인지하기란 사실상 불가능합니다.

**OnGuard**는 다음 문제를 해결하기 위해 개발되었습니다.

| 문제 | OnGuard의 해결 방법 |
|------|---------------------|
| 영상통화 중 딥페이크 실시간 감지 불가 | 온디바이스 AI 모델로 통화 화면을 실시간 분석, 이상 감지 즉시 경고 오버레이 표시 |
| 딥페이크 감지 시 보호자에게 알릴 방법 없음 | 감지 즉시 등록된 보호자에게 FCM 푸시 알림 + 위치 공유 자동 요청 |
| 통화 상대방의 실제 위치 파악 불가 | 상대방 IP 기반 국가 위치를 지도에 시각화하여 표시 |
| 피해 발생 후 대응 수단 부재 | 금융 거래 차단 가이드 오버레이, 최근 감지 기록 타임라인 제공 |

---

## 2. Key Features & System Architecture

### 주요 기능

#### 1. 실시간 딥페이크 감지 경고 오버레이
영상통화 화면 위에 시스템 오버레이(`SYSTEM_ALERT_WINDOW`)로 표시되는 경고 바텀시트.
딥페이크 감지 시 다음 정보를 단계별 애니메이션으로 순차 노출합니다.

- **Stage 1** — 통화 상대방의 국가 위치 (세계 지도 위 핀 + 국기 표시)
- **Stage 2** — 보호자 카카오톡 알림 발송 완료 확인
- **Stage 3** — GPS 위치 공유 상태 확인
- **Stage 4** — 금융 거래 차단 가이드
- **Stage 5** — 전체 대응 완료 요약

#### 2. Agora 기반 영상통화
- 카카오 친구 목록에서 수신자를 선택해 1:1 영상통화 시작
- FCM을 통한 수신 전화 알림 (잠금화면/백그라운드 포함)
- 통화 중 딥페이크 감지 서비스 자동 실행
- 수락 / 거절 / 취소 상태를 FCM 이벤트 버스(`IncomingCallEventBus`)로 동기화

#### 3. 보호자 위치 추적 (GPS)
- 피보호자가 위치 공유를 켜면 보호자 화면에 실시간 위치 표시
- STOMP over WebSocket 구독 방식으로 위치 업데이트 수신
- 딥페이크 감지 이벤트 발생 시 FCM으로 보호자 위치 화면 자동 갱신
- 공유 중인 피보호자가 없으면 WebSocket 연결 자동 해제

#### 4. 보호자 등록 및 관리
- 카카오 친구 목록에서 보호자/피보호자 관계 등록
- 보호자 삭제, 목록 조회

#### 5. 최근 감지 기록 타임라인
- 딥페이크 감지 이력 목록 (날짜별 SessionCard)
- "나" / "보호자" 필터로 본인 / 상대방 이력 분리 조회

#### 6. 카카오 로그인 & 자동 로그인
- 카카오톡 앱 또는 카카오 계정(웹뷰) 로그인
- JWT 자동 갱신 (OkHttp `TokenAuthenticator` — 401 감지 시 자동 refresh)
- 앱 재시작 시 토큰 유효성 확인 후 자동 로그인

---

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Presentation                          │
│  Screens (Compose)  ←→  ViewModels  ←→  Overlay Managers   │
└───────────────────────────┬─────────────────────────────────┘
                            │ UseCase 호출
┌───────────────────────────▼─────────────────────────────────┐
│                          Domain                              │
│         UseCase  ←→  Repository Interface  ←→  Model        │
└───────────────────────────┬─────────────────────────────────┘
                            │ 구현체 주입 (ServiceLocator)
┌───────────────────────────▼─────────────────────────────────┐
│                           Data                               │
│  RepositoryImpl  ←→  Retrofit/OkHttp  ←→  TokenManager     │
│  GpsWebSocketClient (STOMP)    DeepfakeDetector (on-device) │
└─────────────────────────────────────────────────────────────┘

Push: FCM → ShutterFirebaseMessagingService → IncomingCallEventBus / Bridges
DI:   ServiceLocator (수동 싱글톤 컨테이너)
```

**설계 원칙**
- **Clean Architecture**: `presentation` → `domain` → `data` 단방향 의존
- **MVVM**: Compose `StateFlow` 기반 단방향 데이터 흐름
- **Bridge 패턴**: FCM 서비스 ↔ Compose 화면 간 통신 (`CriminalLocationBridge`, `LocationReloadBridge`, `SttTagBridge`)
- **수동 DI**: `ServiceLocator` — Hilt 미사용, 싱글톤 직접 관리

---

### Project Structure

```
app/src/main/java/com/s14p21a404/shutter/
│
├── ShutterApplication.kt              # 앱 진입점, KakaoSdk 초기화
│
├── data/
│   ├── capture/
│   │   └── ScreenCaptureManager.kt    # 화면 캡처 (MediaProjection)
│   ├── detector/                      # 딥페이크 탐지 구현체 (→ AI README 참고)
│   │   ├── DeepfakeDetector.kt
│   │   ├── DeepfakeSettings.kt
│   │   ├── bridge/NativeDeepfakeBridge.kt
│   │   ├── real/
│   │   │   ├── OpenCvFaceTracker.kt
│   │   │   └── RealDeepfakeDetector.kt
│   │   └── stub/BroadcastTriggerDetector.kt
│   ├── repository/
│   │   ├── AlertRepositoryImpl.kt
│   │   ├── AuthRepositoryImpl.kt
│   │   ├── CriminalLocationApi.kt     # 통화 상대 위치 요청 API
│   │   ├── DeepfakeDetectionRepositoryImpl.kt
│   │   ├── GpsRepositoryImpl.kt
│   │   ├── GuardianRepositoryImpl.kt
│   │   ├── VideoCallRepositoryImpl.kt
│   │   └── VideoCallSessionStore.kt
│   └── token/
│       └── TokenManager.kt            # JWT / DeviceUUID SharedPreferences 저장
│
├── di/
│   └── ServiceLocator.kt              # 수동 DI 컨테이너 (싱글톤)
│
├── domain/
│   ├── model/
│   │   ├── DeepfakeDetectionResult.kt
│   │   ├── Friend.kt
│   │   ├── GpsLocation.kt
│   │   ├── Guardian.kt
│   │   ├── KakaoLoginResult.kt
│   │   └── Ward.kt
│   ├── repository/                    # Repository 인터페이스
│   │   ├── AlertRepository.kt
│   │   ├── AuthRepository.kt
│   │   ├── DeepfakeDetectionRepository.kt
│   │   ├── GpsRepository.kt
│   │   ├── GuardianRepository.kt
│   │   └── VideoCallRepository.kt
│   └── usecase/
│       ├── AddGuardianUseCase.kt
│       ├── DeleteGuardianUseCase.kt
│       ├── DetectDeepfakeUseCase.kt
│       ├── GetAccessTokenUseCase.kt
│       ├── GetMyGuardiansUseCase.kt
│       ├── GetMyWardsUseCase.kt
│       ├── GetUserIdUseCase.kt
│       ├── LoginUseCase.kt
│       ├── LogoutUseCase.kt
│       ├── MonitorVideoCallUseCase.kt
│       └── SaveUserIdUseCase.kt
│
├── network/
│   ├── ApiService.kt                  # Retrofit 인터페이스 + DTO
│   ├── AuthEventBus.kt
│   ├── ForbiddenInterceptor.kt
│   ├── GpsWebSocketClient.kt          # STOMP over OkHttp WebSocket
│   ├── RetrofitClient.kt              # OkHttp 클라이언트 + 인터셉터
│   ├── TelemetryApi.kt
│   └── TokenAuthenticator.kt          # 401 자동 토큰 갱신
│
├── presentation/
│   ├── login/
│   │   ├── LoginScreen.kt             # 카카오 로그인 화면
│   │   └── LoginViewModel.kt
│   ├── main/
│   │   ├── MainActivity.kt            # 단일 Activity (FCM Intent 처리 포함)
│   │   ├── MainScreen.kt              # 하단 탭 네비게이션 + 권한 요청 흐름
│   │   └── MainViewModel.kt
│   ├── navigation/
│   │   ├── AppScaffold.kt             # BottomNavigationBar, TopAppBar
│   │   └── Screen.kt                  # 네비게이션 라우트 정의
│   ├── overlay/                       # 시스템 오버레이 (SYSTEM_ALERT_WINDOW)
│   │   ├── FinancialBlockOverlayManager.kt  # 금융 차단 가이드 오버레이
│   │   ├── HoledOverlayView.kt        # ROI 구멍 뚫린 디밍 오버레이
│   │   ├── RoiBoundingBoxView.kt      # 얼굴 바운딩 박스 표시
│   │   ├── ScanningPillView.kt        # "스캔 중" 상태 알림 Pill
│   │   ├── WarningBottomSheetView.kt  # 딥페이크 감지 경고 바텀시트
│   │   └── WarningOverlayManager.kt   # 오버레이 생명주기 총괄 관리
│   ├── screens/
│   │   ├── HomeScreen.kt              # 홈 (통화 시작, 친구 목록)
│   │   ├── HomeViewModel.kt
│   │   ├── GuardianRegistrationScreen.kt  # 보호자 등록
│   │   ├── GuardianRegistrationViewModel.kt
│   │   ├── LocationScreen.kt          # 보호자용 피보호자 위치 지도
│   │   ├── LocationViewModel.kt
│   │   ├── OnDeviceWhisperHelper.kt
│   │   ├── RecentRecordsScreen.kt     # 감지 기록 타임라인
│   │   ├── RecentRecordsViewModel.kt
│   │   ├── SettingsScreen.kt          # 설정 (보호자 목록, 로그아웃)
│   │   ├── SettingsViewModel.kt
│   │   └── VideoCallScreen.kt         # Agora 영상통화 화면
│   └── service/
│       ├── AlertSoundManager.kt       # 경고음 재생
│       ├── CriminalLocationBridge.kt  # FCM ↔ VideoCallScreen 위치 콜백
│       ├── DeepfakeAlertSender.kt     # 보호자 FCM 알림 발송
│       ├── ForegroundAppMonitorService.kt  # 포그라운드 앱 감시 (AccessibilityService)
│       ├── LocationReloadBridge.kt    # FCM ↔ LocationScreen 갱신 콜백
│       ├── SttTagBridge.kt
│       └── VideoCallDetectService.kt  # 통화 중 딥페이크 감지 포그라운드 서비스
│
├── push/
│   ├── IncomingCallEventBus.kt        # 통화 수락/거절/취소 SharedFlow 이벤트 버스
│   └── ShutterFirebaseMessagingService.kt  # FCM 수신 처리 (통화, 위치, 딥페이크 알림)
│
├── telemetry/
│   └── DeviceStatusProvider.kt
│
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

---

## 3. Setup

### 요구 사항

| 항목 | 버전 |
|------|------|
| Android Studio | Ladybug 이상 |
| Kotlin | 2.0+ |
| minSdk | 33 (Android 13) |
| targetSdk | 36 |
| NDK | 21.4.7075529 |
| JDK | 17 |

### 외부 키 설정 — `local.properties`

프로젝트 루트의 `local.properties`에 아래 항목을 추가합니다 (Git 제외 파일).

```properties
# 카카오 디벨로퍼 콘솔에서 발급
kakao.native.app.key=YOUR_KAKAO_NATIVE_APP_KEY

# 백엔드 서버 주소 (trailing slash 필수)
server.base.url=https://your-server.com/

# Agora 콘솔에서 발급
agora.app.id=YOUR_AGORA_APP_ID

# Google Maps API 토큰 (위치 화면용)
GMS_API_TOKEN=YOUR_GMS_API_TOKEN
```

### Firebase 설정

`app/google-services.json` 파일을 Firebase 콘솔에서 다운로드하여 `app/` 디렉토리에 위치시킵니다.

### 카카오 플랫폼 등록

[카카오 디벨로퍼 콘솔](https://developers.kakao.com) → 앱 → Android 플랫폼에 다음을 등록합니다.
- **패키지명**: `com.s14p21a404.shutter`
- **키 해시**: `./gradlew signingReport`로 확인

### 주요 라이브러리

| 라이브러리 | 용도 |
|-----------|------|
| **Jetpack Compose** (BOM) | 전체 UI 프레임워크 |
| `androidx.navigation:navigation-compose` | 화면 간 네비게이션 |
| `io.agora.rtc:full-sdk:4.3.0` | 영상통화 |
| `com.kakao.sdk:v2-user:2.20.1` | 카카오 로그인 |
| `com.kakao.sdk:v2-talk:2.20.1` | 카카오 친구 목록 / 메시지 |
| `com.kakao.sdk:maps` | 위치 지도 |
| `com.google.firebase:firebase-messaging` | FCM 푸시 알림 |
| `com.squareup.retrofit2:retrofit:2.9.0` | REST API 통신 |
| `com.squareup.okhttp3:logging-interceptor:4.12.0` | HTTP 로깅 |
| `io.coil-kt:coil-compose:2.6.0` | 이미지 로딩 (프로필 사진, GIF) |
| `com.github.bumptech.glide:glide:4.16.0` | 오버레이 국기 이미지 로딩 |
| `org.pytorch:pytorch_android:2.1.0` | 온디바이스 딥페이크 모델 (→ AI README) |
| `com.quickbirdstudios:opencv:4.5.3.0` | 얼굴 검출 (→ AI README) |
| `com.microsoft.onnxruntime:onnxruntime-android:1.16.1` | ONNX 추론 폴백 (→ AI README) |
| `com.qualcomm.qti:qnn-runtime:2.34.0` | Qualcomm 하드웨어 가속 (→ AI README) |

---

## 4. Usage

### 빌드 및 실행

```bash
# 1. 저장소 클론
git clone https://lab.ssafy.com/s14-ai-image-sub1/S14P21A404.git
cd S14P21A404/frontend/shutter-android

# 2. local.properties 작성 (위 Setup 참고)

# 3. Android Studio에서 프로젝트 열기 후 Sync
# 또는 CLI로 빌드
./gradlew assembleDebug
```

### 앱 실행 흐름

```
앱 실행
  └─ 토큰 확인
       ├─ 있음 → MainScreen (홈 탭)
       └─ 없음 → LoginScreen
                    └─ 카카오 로그인 → 서버 인증 → MainScreen
```

### 화면별 진입 방법

| 화면 | 진입 방법 |
|------|-----------|
| **홈** | 앱 실행 후 로그인 완료 |
| **영상통화** | 홈 화면에서 친구 선택 → 통화 시작 버튼 / FCM 수신 전화 알림 탭 |
| **위치 추적** | 하단 탭 → 위치 아이콘 (보호자 계정에서 피보호자 위치 확인) |
| **최근 기록** | 하단 탭 → 기록 아이콘 |
| **설정** | 하단 탭 → 설정 아이콘 (보호자 등록/삭제, 로그아웃) |

### 권한 요청

앱 최초 실행 시 `MainScreen`에서 아래 권한을 순차 요청합니다.

- 알림 (`POST_NOTIFICATIONS`)
- 카메라 / 마이크 (영상통화)
- 위치 (`ACCESS_FINE_LOCATION`)
- 다른 앱 위에 표시 (`SYSTEM_ALERT_WINDOW`) — 오버레이 경고창
- 미디어 프로젝션 — 통화 화면 캡처 (딥페이크 감지)

### FCM 메시지 타입

앱이 처리하는 FCM 데이터 메시지 타입 목록입니다.

| `type` 값 | 처리 내용 |
|-----------|-----------|
| `INCOMING_CALL` | 수신 전화 알림 (풀스크린 노티피케이션) |
| `CALL_ACCEPTED` | 발신자측 통화 수락 이벤트 |
| `CALL_REJECTED` | 통화 거절 이벤트 |
| `CALL_CANCELED` | 발신자 통화 취소 이벤트 |
| `LOCATION` | 위치 화면 갱신 트리거 |
| `location_request` | 피보호자 GPS 위치 전송 요청 |
| `criminal_location` | 통화 상대방 국가 위치 수신 → 오버레이 지도 핀 표시 |

---

## 5. Contributors

| 이름 | 담당 |
|------|------|
| 이세원 | UI/UX 디자인, 딥페이크 감지 경고 오버레이, 영상통화 탐지 연동, 금융앱 차단 오버레이 |
| 박수민 | 전체 화면 구현, GPS 위치 추적, 카카오톡 연동, 보호자 등록/관리, 최근 기록 타임라인, 서버 연동 |
| 정다진 | Agora 영상통화(요청·수락·거절), NDK, 온디바이스 딥페이크 탐지, 영상 전처리, 탐지 결과 집계 |
| 성주희 | 딥페이크 경고창·탐지 영역 표시, NDK, 온디바이스 STT/LLM 대화 분석, OpenCV 얼굴 탐지, HTP→GPU→CPU 폴백, QNN/NNAPI 모델 최적화, INT8 양자화, 탐지 결과 서버 전송 |
| 장유진 | NDK, 온디바이스 딥페이크 탐지, 모델 성능 향상, CAM 기반 ROI 추출, PyTorch→ONNX 변환 |

---

> AI 탐지 모델 및 서버 사이드 딥페이크 처리 내용은 별도 AI README를 참고하세요.
