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