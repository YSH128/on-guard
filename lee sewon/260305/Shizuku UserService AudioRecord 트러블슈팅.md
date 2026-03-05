# Shizuku UserService AudioRecord 트러블슈팅

## 📅 작성일: 2026-03-05

---

## 1. 우리가 하려는 것

영상통화 중인 화면과 음성을 캡처해서 딥페이크 감지 AI에 넘기려고 합니다.  
일반 앱 권한으로는 다른 앱의 화면/음성에 접근할 수 없기 때문에, **Shizuku**라는 라이브러리를 사용합니다.

### Shizuku란?
- 안드로이드에서 `adb shell` 수준의 권한(UID 2000, Shell 권한)을 앱에게 빌려주는 라이브러리
- 우리 앱의 코드를 **별도 프로세스(`app_process`)**로 실행시켜주는데, 이 프로세스는 Shell 권한으로 동작함
- 이 Shell 권한 프로세스를 **UserService**라고 부르며, 우리 코드에서는 `MediaCaptureUserService`가 해당

---

## 2. 현재 상태 (무엇이 되고, 무엇이 안 되는가)

| 기능 | 상태 | 설명 |
|------|------|------|
| **비디오 캡처** | ⚠️ 부분 성공 | `SurfaceControl.createDisplay`가 SDK 36에서 삭제되어 실패 → `screencap` 명령어 폴백으로 동작 중 (2초 간격 스크린샷) |
| **오디오 캡처** | ❌ 실패 | `AudioRecord` 생성 자체가 `UnsupportedOperationException: Cannot create AudioRecord`로 실패 |

---

## 3. 오디오 캡처가 안 되는 이유 (핵심 문제)

### 3-1. AudioRecord가 내부적으로 하는 일

`AudioRecord`를 생성하면 내부에서 이런 일이 벌어집니다:

```
AudioRecord.Builder.build()
  → new AudioRecord(...)
    → native_setup()  ← C++ 네이티브 코드
      → AudioPolicyService에게 "나 녹음할게" 요청
        → 이때 "누가 요청하는지" 확인 (패키지명 + AttributionSource)
```

이 **"누가 요청하는지"**를 알아내기 위해 AudioRecord는 내부적으로:
```java
Context context = ActivityThread.currentApplication();
String packageName = context.getOpPackageName();
AttributionSource source = context.getAttributionSource();
```
를 호출합니다.

### 3-2. Shizuku UserService 환경의 특수성

일반 앱은 Android 시스템이 자동으로 `ActivityThread` → `Application` → `Context`를 만들어줍니다.  
하지만 **Shizuku UserService는 `app_process`로 직접 실행되는 프로세스**이기 때문에:

- `ActivityThread`는 있지만, 그 안의 `Application`이 시스템에서 자동 생성한 것
- 이 Application의 `getOpPackageName()`이 `"android"` 같은 시스템 패키지명을 반환
- `getAttributionSource()`의 UID/패키지 정보가 AudioPolicyService에 등록된 것과 불일치
- → **AudioPolicyService가 녹음 요청을 거부** → `Cannot create AudioRecord`

### 3-3. 시도한 해결 방법들과 결과

| 시도 | 방법 | 결과 |
|------|------|------|
| 1차 | `ActivityThread.systemMain()`으로 Application 수동 생성 | Application은 생성되지만 패키지명이 `"android"`여서 AudioRecord 여전히 실패 |
| 2차 | `FakeContext`(ContextWrapper 상속)를 만들어 `mInitialApplication` 교체 | `mInitialApplication` 필드가 `Application` 타입인데 `ContextWrapper`를 넣어서 `IllegalArgumentException` |
| 3차 | `FakeContext`를 `Application` 상속으로 변경 | Shizuku가 별도 ClassLoader를 사용하므로, 시스템 ClassLoader의 `Application`과 앱 ClassLoader의 `Application`이 **서로 다른 클래스로 인식**되어 여전히 `IllegalArgumentException` |
| 4차 (현재) | 기존 Application 객체는 그대로 두고, 내부 `ContextImpl`의 필드(`mOpPackageName`, `mAttributionSource`)를 리플렉션으로 직접 패치 | 패치 자체는 성공, AudioRecord 생성은 여전히 실패 → **AudioPolicyService 레벨의 권한 검증 문제로 추정** |

---

## 4. 왜 패키지명을 바꿔도 안 되는가

로그를 보면 패치 후 검증 결과:
```
검증: packageName=com.s14p21a404.shutter
검증: opPackageName=com.s14p21a404.shutter
```

패키지명은 올바르게 바뀌었습니다. 그런데도 AudioRecord가 실패하는 이유는:

1. **UID 불일치**: 우리 프로세스는 UID=2000(shell)인데, `com.s14p21a404.shutter` 패키지는 UID=10426(앱 UID)으로 등록되어 있음. AudioPolicyService가 "UID 2000인데 왜 UID 10426의 앱이라고 주장하냐?"고 거부
2. **AppOps 토큰 미등록**: Android 12+에서 `AttributionSource`에는 Binder 토큰이 포함되는데, 우리가 만든 `AttributionSource`의 토큰은 `AppOpsService`에 등록되지 않음
3. **SDK 36(Android 16) 강화된 보안**: 최신 버전에서 shell UID의 오디오 녹음 권한이 더 엄격해짐

### 4-1. 근본 원인: UID 2000(shell)에는 RECORD_AUDIO 권한 자체가 없다

어떤 패키지명을 쓰든 결과는 동일합니다:

| 시나리오 | 패키지명 | AudioPolicyService 판단 |
|----------|----------|------------------------|
| 우리 앱 패키지로 위장 | `com.s14p21a404.shutter` | "UID 2000인데 UID 10426의 앱이라고? → **UID 불일치 → 거부**" |
| shell 패키지로 정직하게 | `com.android.shell` | "UID 2000, 패키지 일치함. 근데 `RECORD_AUDIO` 권한 있나? → **없음 → 거부**" |

**결론**: 문제는 패키지명이 아니라, **UID 2000(shell)에 `RECORD_AUDIO` 퍼미션이 부여되어 있지 않다**는 것입니다.

`screencap`이 되는 이유는 shell에 화면 캡처 관련 권한(`ACCESS_SURFACE_FLINGER` 등)은 있기 때문이고, 오디오 녹음 권한(`RECORD_AUDIO`)은 없기 때문에 AudioRecord는 불가능합니다.

이것은 FakeContext든 리플렉션이든 어떤 Java 레벨 우회로도 해결할 수 없는 **OS 커널/SELinux 레벨의 제한**입니다.

---

## 5. 비디오 캡처는 왜 부분적으로 되는가

| Android 버전 | SurfaceControl.createDisplay | 우리 대응 |
|-------------|------------------------------|-----------|
| ~Android 13 | ✅ 존재 | SurfaceControl로 가상 디스플레이 생성 → ImageReader로 프레임 획득 |
| Android 14+ | ❌ 삭제됨 | `screencap` 명령어 실행 → PNG 파일 생성 → 디코드 → 리사이즈 → RGBA byte[] 전달 |

현재 SDK 36 기기에서는 `screencap` 폴백으로 **2초 간격 스크린샷**이 동작 중입니다.  
실시간 프레임 캡처는 아니지만, 딥페이크 감지용으로는 사용 가능합니다.

---

## 6. 현재 코드 구조

```
MediaCaptureUserService (Shizuku UserService, UID=2000)
  ├── FakeContext.ensureInitialized()  ← ContextImpl 필드 패치
  ├── PrivilegedVideoCapture
  │     ├── 방식1: SurfaceControl.Transaction + createDisplay (SDK≤13)
  │     ├── 방식2: Legacy openTransaction/closeTransaction (SDK≤11)
  │     └── 방식3: screencap 프로세스 실행 (SDK 14+) ← 현재 이것만 동작
  └── PrivilegedAudioCapture
        ├── 방식1: AudioRecord.Builder + setContext() (SDK 34+)
        ├── 방식2: AudioRecord.Builder 일반 (mInitialApplication 패치 의존)
        └── 방식3: 리플렉션 AudioRecord 생성자 직접 호출
        → 모두 실패 중
```

---

## 7. 앞으로의 방향

### 오디오 캡처 해결 방안

Java API(`AudioRecord`)로는 UID 2000에서 절대 녹음 불가능하므로, 다른 경로가 필요합니다.

| 방안 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **셸 명령어 우회** | `screenrecord`로 MP4 파일 녹화 후 오디오 트랙 추출, 또는 ALSA 디바이스(`tinycap`) 직접 접근 | Shizuku만으로 가능 | 실시간성 떨어짐, 기기 호환성 불확실 |
| **MediaProjection 병행** | 비디오는 Shizuku(screencap), 오디오는 앱 본체에서 MediaProjection(사용자 동의 팝업)으로 캡처 | 안정적, 공식 API | 사용자에게 "화면 녹화" 동의 팝업 표시됨 |
| **`appops set` 권한 부여** | `adb shell appops set com.android.shell RECORD_AUDIO allow`로 shell에 녹음 권한 부여 | 간단, Shizuku에서 실행 가능 | 재부팅 시 초기화될 수 있음, 보안 위험 |
| **root 권한** | Magisk 등으로 UID=0에서 실행하면 모든 제한 해제 | 확실한 해결 | root 필요, 일반 사용자 불가 |

### 비디오 캡처 개선

- 현재 screencap 방식은 2초 간격 → 실시간이 아님
- `ScreenCapture` hidden API나 `SurfaceFlinger` 직접 접근 검토 필요

---

## 8. 개발/테스트 시 주의사항

### Shizuku UserService 프로세스 캐시 문제
- 코드를 수정해도 **기존 UserService 프로세스가 살아있으면 옛날 코드가 실행됨**
- 반드시 기존 프로세스를 죽여야 함:
  ```bash
  # capture 프로세스 확인
  adb shell ps -A | grep capture
  
  # PID로 강제 종료
  adb shell kill -9 <PID>
  ```
- 또는 Shizuku 앱에서 서비스 재시작

### 앱 재설치
- `Rebuild Project`만으로는 기기에 반영 안 됨
- 반드시 **Run(▶️)** 또는 `adb install`로 재설치 필요

### 로그 확인
```bash
adb logcat -s FakeContext PrivilegedAudioCapture PrivilegedVideoCapture MediaCaptureUserService
```

