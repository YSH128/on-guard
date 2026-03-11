## 로그인 흐름 구현 가이드 (Kakao Login + JWT)

본 문서는 **Android 앱에서 카카오 로그인과 서버 JWT 인증을 사용하는 전체 로그인 흐름**을 설명한다.

---

# 1. 전체 앱 흐름

앱 실행 시 **로그인 상태 여부에 따라 화면이 분기된다.**

```
앱 실행
 ↓
로컬에 JWT AccessToken 존재 여부 확인
 ↓
[토큰 없음]
LoginScreen 이동 (카카오 로그인 버튼)

[토큰 있음]
MainScreen 이동
```

---

# 2. 화면 흐름

## 1️⃣ 로그인 안된 상태

앱 실행 시

```
LoginScreen
↓
카카오 로그인 버튼 클릭
↓
카카오 SDK 로그인
↓
카카오 토큰 획득
↓
POST /api/v1/auth/kakao
↓
서버 JWT 발급
↓
토큰 저장
↓
MainScreen 이동
```

---

## 2️⃣ 로그인 된 상태에서 앱 재실행

```
앱 실행
↓
저장된 access_token 확인
↓
존재하면
↓
MainScreen 바로 이동
```

---

# 3. 토큰 저장 방식

토큰은 **SharedPreferences 또는 DataStore**에 저장한다.

권장 구조

```
access_token
refresh_token
```

예시

```
prefs.edit()
    .putString("access_token",accessToken)
    .putString("refresh_token",refreshToken)
    .apply()
```

---

# 4. 카카오 로그인

카카오 SDK 로그인 성공 시 다음 토큰을 획득한다.

```
kakao_access_token
kakao_refresh_token

```

이 값을 서버 로그인 API에 전달한다.

---

# 5. 서버 로그인 API

## POST `/api/v1/auth/kakao`

카카오 토큰을 서버로 보내 **서비스 JWT를 발급받는다.**

### Request

```
{
  "kakao_access_token":"카카오_access_token",
  "kakao_refresh_token":"카카오_refresh_token"
}
```

---

### Response

```
{
  "success":true,
  "message":"로그인 성공",
  "data": {
    "access_token":"서버_JWT_access_token",
    "refresh_token":"서버_JWT_refresh_token"
  }
}
```

---

# 6. 토큰 저장

로그인 성공 시

```
access_token 저장
refresh_token 저장
```

이후 모든 API 요청에 access token 사용

---

# 7. API 요청 시 인증

모든 API 요청 header

```
Authorization: Bearer {access_token}
```

---

# 8. Access Token 만료 처리

AccessToken은 **1시간 후 만료**된다.

만료 시 아래 API로 재발급한다.

---

# 9. Access Token 재발급

## POST `/api/v1/auth/refresh`

### Request

```
{
  "refresh_token":"기존_refresh_token"
}
```

---

### Response

```
{
  "success":true,
  "message":"토큰 갱신 성공",
  "data":"새로운_access_token"
}
```

프론트 처리

```
새 access_token 저장
```

---

# 10. Refresh Token 만료 처리

다음 오류 발생 시

```
INVALID_REFRESH_TOKEN
```

의미

```
Refresh Token 만료 또는 위조
```

프론트 처리

```
토큰 삭제
LoginScreen 이동
```

---

# 11. 화면 구조 예시

```
app
 ├─ ui
 │   ├─ LoginScreen.kt
 │   └─ MainScreen.kt
 │
 ├─ auth
 │   ├─ AuthRepository.kt
 │   └─ TokenManager.kt
 │
 ├─ network
 │   └─ ApiService.kt
```

---

# 12. 구현 체크리스트

- LoginScreen 구현
- 카카오 로그인 SDK 연동
- `/api/v1/auth/kakao` 호출
- JWT 토큰 저장
- 앱 실행 시 로그인 상태 체크
- MainScreen 자동 이동
- 토큰 만료 시 `/auth/refresh` 호출