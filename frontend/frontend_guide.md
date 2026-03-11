# Frontend Guide — Shutter Android

> 대상 경로: `frontend/shutter-android/`
> 플랫폼: Android (Kotlin + Jetpack Compose)
> 최소 SDK: 33 (Android 13), 타깃 SDK: 36

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [폴더 구조](#2-폴더-구조)
3. [핵심 설정 파일](#3-핵심-설정-파일)
4. [아키텍처 — Clean Architecture](#4-아키텍처--clean-architecture)
5. [레이어별 상세 설명](#5-레이어별-상세-설명)
   - [Presentation Layer](#51-presentation-layer)
   - [Domain Layer](#52-domain-layer)
   - [Data Layer](#53-data-layer)
6. [상태 관리](#6-상태-관리)
7. [데이터 플로우](#7-데이터-플로우)
8. [UI 스타일링 (Compose + Material3)](#8-ui-스타일링-compose--material3)
9. [안드로이드 시스템 서비스 연동](#9-안드로이드-시스템-서비스-연동)
10. [JNI / 네이티브 연동](#10-jni--네이티브-연동)
11. [오버레이 시스템](#11-오버레이-시스템)
12. [의존성 주입 (DI)](#12-의존성-주입-di)
13. [테스트 전략](#13-테스트-전략)
14. [권한 흐름](#14-권한-흐름)

---

## 1. 프로젝트 개요

Shutter Android는 영상통화 중 딥페이크를 실시간으로 감지하는 **보안 앱**이다.
감지 결과에 따라 화면 위에 경고 오버레이를 표시하며, 백그라운드 포그라운드 서비스로 동작한다.

**핵심 기능:**
- 영상통화 자동 감지 (TelephonyCallback + CameraManager)
- 화면 캡처 후 AI 딥페이크 분석 (MediaProjection → JNI → C++ 모델)
- 감지 시 글래스모피즘 경고 오버레이 표시
- ADB 브로드캐스트를 통한 테스트 스텁

---

## 2. 폴더 구조

```
frontend/shutter-android/
├── app/
│   ├── build.gradle.kts                      # 앱 빌드 설정
│   └── src/main/
│       ├── AndroidManifest.xml               # 권한·서비스 선언
│       └── java/com/s14p21a404/shutter/
│           ├── data/                         # Data Layer
│           │   ├── capture/
│           │   │   └── ScreenCaptureManager.kt
│           │   ├── detector/
│           │   │   ├── DeepfakeDetector.kt        # 인터페이스
│           │   │   ├── bridge/
│           │   │   │   └── NativeDeepfakeBridge.kt  # JNI 브릿지
│           │   │   ├── real/
│           │   │   │   └── RealDeepfakeDetector.kt  # 실제 AI 감지
│           │   │   └── stub/
│           │   │       └── BroadcastTriggerDetector.kt # ADB 테스트 스텁
│           │   └── repository/
│           │       ├── DeepfakeDetectionRepositoryImpl.kt
│           │       └── VideoCallRepositoryImpl.kt
│           ├── di/
│           │   └── ServiceLocator.kt              # 수동 DI 컨테이너
│           ├── domain/                        # Domain Layer
│           │   ├── model/
│           │   │   └── DeepfakeDetectionResult.kt
│           │   ├── repository/
│           │   │   ├── DeepfakeDetectionRepository.kt  # 인터페이스
│           │   │   └── VideoCallRepository.kt          # 인터페이스
│           │   └── usecase/
│           │       ├── DetectDeepfakeUseCase.kt
│           │       └── MonitorVideoCallUseCase.kt
│           ├── presentation/                  # Presentation Layer
│           │   ├── main/
│           │   │   ├── MainActivity.kt        # Compose 기반 메인 액티비티
│           │   │   ├── MainScreen.kt          # Compose UI
│           │   │   └── MainViewModel.kt       # UI 상태 관리
│           │   ├── overlay/
│           │   │   ├── HoledOverlayView.kt    # 원형 구멍이 있는 커스텀 뷰
│           │   │   └── WarningOverlayManager.kt # 오버레이 윈도우 관리
│           │   └── service/
│           │       └── VideoCallDetectService.kt # 포그라운드 서비스
│           └── ui/theme/
│               ├── Color.kt
│               ├── Theme.kt
│               └── Type.kt
├── gradle/
│   └── libs.versions.toml                    # 버전 카탈로그
└── settings.gradle.kts
```

---

## 3. 핵심 설정 파일

### `gradle/libs.versions.toml` — 버전 카탈로그

| 항목 | 버전 |
|------|------|
| AGP (Android Gradle Plugin) | 9.0.1 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.09.00 (Material3) |
| Androidx Lifecycle | 2.10.0 |
| Androidx Activity | 1.12.4 |
| Core KTX | 1.17.0 |

### `app/build.gradle.kts` — 앱 설정

```kotlin
android {
    compileSdk = 36
    defaultConfig {
        minSdk = 33          // Android 13 이상
        targetSdk = 36
    }
    buildFeatures {
        compose = true       // Jetpack Compose 활성화
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
```

### `AndroidManifest.xml` — 권한 목록

| 권한 | 용도 |
|------|------|
| `READ_PHONE_STATE` | 통화 상태 감지 |
| `FOREGROUND_SERVICE` | 백그라운드 서비스 실행 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 화면 캡처 서비스 |
| `POST_NOTIFICATIONS` | 알림 표시 |
| `SYSTEM_ALERT_WINDOW` | 오버레이 표시 |

---

## 4. 아키텍처 — Clean Architecture

```
┌──────────────────────────────────────┐
│         Presentation Layer           │
│  (MainActivity, MainScreen,          │
│   ViewModel, Service, Overlay)       │
└────────────────┬─────────────────────┘
                 │ 도메인 인터페이스 사용
┌────────────────▼─────────────────────┐
│           Domain Layer               │
│  (UseCase, Repository 인터페이스,    │
│   Model)                             │
└────────────────┬─────────────────────┘
                 │ 인터페이스 구현
┌────────────────▼─────────────────────┐
│            Data Layer                │
│  (RepositoryImpl, Detector,          │
│   ScreenCaptureManager, JNI Bridge)  │
└──────────────────────────────────────┘
```

- **단방향 의존성**: Presentation → Domain ← Data
- Domain은 Android SDK에 의존하지 않는 순수 Kotlin
- ServiceLocator가 구현체를 주입

---

## 5. 레이어별 상세 설명

### 5.1 Presentation Layer

#### `MainActivity.kt`
- `ComponentActivity` + Compose UI
- 런타임 권한 요청 (`ActivityResultContracts`)
- MediaProjection 권한 요청 → `VideoCallDetectService` 시작/중지
- 세 가지 권한 흐름 처리: 전화 상태, 알림, 오버레이

#### `MainScreen.kt`
Compose 단일 화면. 세 가지 상태에 따라 다른 버튼 표시:

| 상태 | 표시 버튼 |
|------|-----------|
| 일반 권한 미허용 | "권한 허용하기" |
| 오버레이 권한 미허용 | "오버레이 권한 허용하기" |
| 모든 권한 허용 | "보호 시작" / "보호 중지" 토글 |

#### `MainViewModel.kt`
```kotlin
data class MainUiState(
    val isServiceRunning: Boolean = false,
    val allPermissionsGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false
)
```
- `AndroidViewModel` 상속 (앱 Context 접근)
- `MutableStateFlow<MainUiState>` 기반 반응형 상태

#### `VideoCallDetectService.kt`
- 포그라운드 서비스 (알림 필수)
- `serviceScope` (SupervisorJob + Dispatchers.Default)
- 두 개의 코루틴 동시 실행:
  1. 영상통화 모니터링 → 감지 시 딥페이크 탐지 시작
  2. 탐지 결과 수집 → 오버레이 표시/숨김

### 5.2 Domain Layer

#### 모델

```kotlin
data class DeepfakeDetectionResult(
    val detected: Boolean,
    val roi: FloatArray? = null,       // [minX, minY, maxX, maxY] 정규화 좌표
    val timestamp: Long = System.currentTimeMillis()
) {
    val roiMinX: Float get() = roi?.getOrNull(0) ?: 0f
    val roiMinY: Float get() = roi?.getOrNull(1) ?: 0f
    val roiMaxX: Float get() = roi?.getOrNull(2) ?: 0f
    val roiMaxY: Float get() = roi?.getOrNull(3) ?: 0f
    val hasRoi: Boolean get() = roi != null && roi.size == 4
}
```

#### 리포지토리 인터페이스

```kotlin
interface DeepfakeDetectionRepository {
    fun observeDetectionResults(): Flow<DeepfakeDetectionResult>
    fun startDetection()
    fun stopDetection()
    fun setMediaProjection(context: Context, mediaProjection: MediaProjection)
    fun releaseMediaProjection()
}

interface VideoCallRepository {
    fun observeVideoCallState(): Flow<Boolean>
    fun startMonitoring()
    fun stopMonitoring()
}
```

#### 유즈케이스

- **`MonitorVideoCallUseCase`**: 영상통화 상태 관찰 → 자동으로 탐지 시작/중지 제어
- **`DetectDeepfakeUseCase`**: 탐지 결과 Flow를 Presentation에 노출

### 5.3 Data Layer

#### `DeepfakeDetectionRepositoryImpl`
- `DeepfakeDetector` 구현체를 사용해 AI 분석 수행
- 결과를 `MutableSharedFlow<DeepfakeDetectionResult>`로 방출

#### `VideoCallRepositoryImpl`
```kotlin
// 영상통화 판단 조건: 통화 중 AND 카메라 사용 중
private fun evaluate(): Boolean = isOffhook && isCameraBusy
```
- `TelephonyCallback.CallStateListener` — 통화 상태 감지
- `CameraManager.AvailabilityCallback` — 카메라 사용 감지
- `MutableStateFlow<Boolean>` 로 상태 방출

#### `ScreenCaptureManager`
- `MediaProjection` → `ImageReader` + `VirtualDisplay`
- 기본 샘플링 간격: 2000ms
- RGBA_8888 버퍼를 패딩 제거 후 JNI로 전달

#### `DeepfakeDetector` 인터페이스

```kotlin
interface DeepfakeDetector {
    fun startDetection(onResult: (DeepfakeDetectionResult) -> Unit)
    fun stopDetection()
    fun processFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int) { }
}
```

구현체:
| 구현체 | 용도 |
|--------|------|
| `RealDeepfakeDetector` | JNI → C++ AI 모델 (프로덕션) |
| `BroadcastTriggerDetector` | ADB 브로드캐스트 수신 (테스트) |

---

## 6. 상태 관리

Kotlin Coroutines + Flow 기반 (Redux/MobX 없음)

| 계층 | Flow 타입 | 이유 |
|------|-----------|------|
| 영상통화 상태 | `StateFlow<Boolean>` | 현재 상태가 항상 필요 (stateful) |
| 탐지 결과 | `SharedFlow<DeepfakeDetectionResult>` | 이벤트성 데이터 (stateless) |
| UI 상태 | `StateFlow<MainUiState>` | Compose 리컴포지션 트리거 |

**Flow 연산 패턴:**
```kotlin
// ViewModel에서 Compose State로 수집
val uiState by viewModel.uiState.collectAsStateWithLifecycle()

// Service에서 상태 기반 제어
monitorUseCase.observeAndControl()
    .collect { isVideoCall ->
        if (isVideoCall) startDetection() else stopDetection()
    }
```

---

## 7. 데이터 플로우

```
사용자가 "보호 시작" 버튼 클릭
        ↓
MainActivity → MediaProjection 권한 요청
        ↓
VideoCallDetectService 시작 + MediaProjection 전달
        ↓
┌──────────────────────────────┐
│  monitorUseCase.observe()    │  ← TelephonyCallback + CameraManager
│  영상통화 감지 시 탐지 시작  │
└──────────────┬───────────────┘
               ↓ 영상통화 감지
ScreenCaptureManager → 2초마다 프레임 캡처
               ↓
    RGBA ByteArray → NativeDeepfakeBridge (JNI)
               ↓
     C++ AI 모델 분석 결과 반환
               ↓
DeepfakeDetectionRepositoryImpl → SharedFlow 방출
               ↓
VideoCallDetectService → WarningOverlayManager
               ↓
    detected=true → 경고 오버레이 표시
    detected=false → 오버레이 숨김
```

---

## 8. UI 스타일링 (Compose + Material3)

XML 레이아웃 없음. 전부 Jetpack Compose.

### 테마 구조

```
ui/theme/
├── Color.kt    — 색상 팔레트 정의
├── Theme.kt    — Material3 ColorScheme + 다크모드 지원
└── Type.kt     — 타이포그래피 스케일
```

### 주요 색상

```kotlin
// Light Theme
val Purple40 = Color(0xFF6650A4)   // Primary

// Dark Theme
val Purple80 = Color(0xFFD0BCFF)   // Primary

// UI 직접 사용 색상
Color(0xFFE8F5E9)   // 서비스 활성 상태 (연두)
Color(0xFFFF9800)   // 권한 요청 버튼 (주황)
Color(0xFFFF5722)   // 오버레이 권한 버튼 (붉은 주황)
```

### Compose 컴포넌트 패턴

```kotlin
// MainScreen 구조
Scaffold {
    Column {
        Text("🛡️ Shutter")           // 타이틀
        Card { /* 서비스 상태 */ }    // 상태 카드
        Spacer(48.dp)
        when {
            !allPermissionsGranted -> Button("권한 허용하기")
            !overlayGranted       -> Button("오버레이 권한 허용하기")
            else                  -> Button("보호 시작/중지")  // 토글
        }
    }
}
```

### 다이나믹 컬러 (Android 12+)

```kotlin
val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (darkTheme) dynamicDarkColorScheme(context)
    else dynamicLightColorScheme(context)
} else { /* 기본 팔레트 사용 */ }
```

---

## 9. 안드로이드 시스템 서비스 연동

| 시스템 서비스 | 클래스 | 용도 |
|-------------|--------|------|
| `TelephonyManager` | `VideoCallRepositoryImpl` | 통화 상태 감지 |
| `CameraManager` | `VideoCallRepositoryImpl` | 카메라 점유 감지 |
| `MediaProjectionManager` | `MainActivity` | 화면 캡처 권한 |
| `WindowManager` | `WarningOverlayManager` | 오버레이 윈도우 |
| `NotificationManager` | `VideoCallDetectService` | 포그라운드 알림 |

**영상통화 판단 로직:**
```kotlin
// 두 조건이 모두 true일 때만 영상통화로 판단
isOffhook (TelephonyCallback) && isCameraBusy (CameraManager)
```

---

## 10. JNI / 네이티브 연동

```
Kotlin (RealDeepfakeDetector)
        ↓ ByteArray + 메타데이터
NativeDeepfakeBridge.kt (JNI 선언)
        ↓
libshutter-native.so (C++ AI 모델)
        ↓ JSON 결과 문자열
RealDeepfakeDetector.parseResult()
        ↓
DeepfakeDetectionResult
```

**JNI 시그니처:**
```kotlin
object NativeDeepfakeBridge {
    init { System.loadLibrary("shutter-native") }

    external fun request_deepfake_detection(
        type: String,
        buffer: ByteArray,
        resultTextView: TextView   // 결과 수신 뷰
    )
}
```

**프레임 처리:**
- 포맷: `PixelFormat.RGBA_8888`
- 패딩 제거 후 실제 픽셀 데이터만 추출
- 최신 프레임은 `ScreenCaptureManager.latestBitmap`에 캐시

---

## 11. 오버레이 시스템

`WarningOverlayManager`가 6개 윈도우를 조합해 구현:

```
┌─────────────────────────┐
│   글래스 카드 (경고)     │  ← TYPE_APPLICATION_OVERLAY (터치 수신)
├─────────────────────────┤
│   원형 구멍 딤 오버레이  │  ← HoledOverlayView (터치 통과)
├────┬──────┬──────┬──────┤
│ ①  │  ②   │  ③   │  ④  │  ← 4개 터치 차단 스트립
└────┴──────┴──────┴──────┘
```

**`HoledOverlayView`:**
- `Canvas.drawCircle` + `PorterDuff.Mode.CLEAR` 로 구멍 생성
- `RadialGradient`로 경계 그라디언트 효과

**오버레이 WindowManager 플래그:**
```kotlin
WindowManager.LayoutParams.apply {
    type = TYPE_APPLICATION_OVERLAY
    flags = FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN
    format = PixelFormat.TRANSLUCENT
}
```

---

## 12. 의존성 주입 (DI)

**수동 ServiceLocator 패턴** (Hilt 미사용):

```kotlin
object ServiceLocator {
    val videoCallRepository: VideoCallRepository by lazy {
        VideoCallRepositoryImpl(/* ... */)
    }

    val deepfakeDetector: DeepfakeDetector by lazy {
        RealDeepfakeDetector()          // 프로덕션
        // BroadcastTriggerDetector()   // 테스트용 (주석 토글)
    }

    val deepfakeDetectionRepository: DeepfakeDetectionRepository by lazy {
        DeepfakeDetectionRepositoryImpl(deepfakeDetector)
    }
}
```

> 향후 Hilt 마이그레이션을 고려하고 있음 (주석에 언급).

---

## 13. 테스트 전략

| 테스트 종류 | 위치 | 도구 |
|------------|------|------|
| 유닛 테스트 | `src/test/` | JUnit |
| 계측 테스트 | `src/androidTest/` | Espresso |
| 수동 감지 트리거 | ADB 브로드캐스트 | `BroadcastTriggerDetector` |

**ADB 테스트 스텁 사용법:**
```bash
# 딥페이크 감지 시뮬레이션
adb shell am broadcast -a com.s14p21a404.shutter.DEEPFAKE_DETECTED

# 감지 해제 시뮬레이션
adb shell am broadcast -a com.s14p21a404.shutter.DEEPFAKE_CLEARED
```

---

## 14. 권한 흐름

```
앱 실행
    ↓
READ_PHONE_STATE 권한 확인
    → 미허용 시: 시스템 권한 다이얼로그 표시
    ↓
POST_NOTIFICATIONS 권한 확인 (Android 13+)
    → 미허용 시: 시스템 권한 다이얼로그 표시
    ↓
SYSTEM_ALERT_WINDOW (오버레이) 권한 확인
    → 미허용 시: 시스템 설정 화면으로 이동
    ↓
모든 권한 허용 → "보호 시작" 버튼 활성화
    ↓
버튼 클릭 → MediaProjection 권한 요청 (매번 사용자 확인 필요)
    ↓
VideoCallDetectService 시작
```

---

## 기술 스택 요약

| 항목 | 기술 |
|------|------|
| 언어 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| 비동기 | Kotlin Coroutines + Flow |
| 아키텍처 | Clean Architecture (3-tier) |
| DI | 수동 ServiceLocator |
| 빌드 | Gradle 9.0.1 (버전 카탈로그) |
| 네이티브 | JNI → C++ (libshutter-native.so) |
| 최소 SDK | 33 (Android 13) |
| 테스트 | JUnit + Espresso |
