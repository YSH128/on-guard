# 0. 통신사 영상통화 및 카카오톡 페이스톡 감지 전략

[1] 통신사 영상통화는 Telephony API를 활용하여 감지 가능

[2] 카카오톡 페이스톡은 패키지 + 카메라 + AudioMode 조합이 가장 현실적인 전략

[3] 100% 확정 감지는 불가능하지만, 다중 조건 조합으로 높은 정확도를 확보할 수 있음

# 0-1. 통신사 영상통화 감지

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

# 0-2. 카카오톡 페이스톡 감지

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


# 1. 안드로이드 스튜디오 개발 기초

## 1) 개발 환경 세팅

[1] Android Studio 설치

[2] Empty Activity 기반 프로젝트 생성 (추후 확장 용이)

[3] 프로젝트 로딩 완료 후 Split 모드로 Design Preview 확인

---

## 2) 자주 사용한 기능 및 코드 수정

[1] import 정리

* 단축키: `Ctrl + Alt + O`
* 기능: import문 알파벳 순 정렬 및 미사용 import 제거

[2] 텍스트 배경색 수정

* `Alt + Enter` → `Wrap with Surface()`
* `color = Color.Black` 설정

[3] 패딩값 추가

```kotlin
modifier = Modifier.padding(24.dp)
```

---

# 2. 가상 기기(AVD) 추가

[1] Device Manager 실행

[2] Create Device 선택

[3] SDK System Image 다운로드

[4] Finish 후 가상 기기 생성

---

# 3. 실제 스마트폰 연결 테스트

## 1) 개발자 모드 활성화

[1] 설정 → 휴대전화 정보

[2] 소프트웨어 정보

[3] 빌드번호 7번 터치

---

## 2) USB 디버깅 설정

[1] 설정 → 개발자 옵션

[2] USB 디버깅 ON

---

## 3) PC 설정

[1] Android Studio → Tools → SDK Manager → SDK Tools

[2] Google USB Driver 설치

---

## 4) 실행

[1] 스마트폰 USB 연결

[2] Android Studio에서 디바이스 선택 후 Run

---

## 5) 개발자 모드 비활성화

[1] 개발자 옵션 → OFF

[2] 보안 위험 자동 차단 ON

---

# 4. 안드로이드 테스트 구조

## 1) 단위 테스트 (ExampleUnitTest)

[1] 실행 위치: PC

[2] Android Framework 접근 불가

[3] 순수 Kotlin/Java 로직 테스트

[4] 실행 속도 빠름

---

## 2) 계측 테스트 (ExampleInstrumentedTest)

[1] 실행 위치: 에뮬레이터 또는 실제 폰

[2] Android 환경 접근 가능

[3] UI, DB, Activity 테스트 가능

[4] 실행 속도 느림