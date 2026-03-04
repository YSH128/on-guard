딥페이크 탐지 서비스 Telemetry 모니터링 서버 설계 문서

작성 기준: 2026-03-04 (Asia/Seoul)

목표

On-device AI 모델(FACE / DEEPVOICE)의 추론 실행 상태를 서버에서 수집하여

운영 모니터링 및 성능 분석을 수행한다.

서버는 AI 추론을 수행하지 않으며 **Telemetry 데이터를 수집 및 저장하는 역할만 담당한다.**

---

# 0) 설계 원칙

1. AI 모델은 **모바일 디바이스에서 On-device로 실행된다.**
2. 서버는 **AI 추론 판단을 수행하지 않는다.**
3. 서버는 **Telemetry 데이터 수집 및 저장 역할만 수행한다.**
4. 원본 영상 및 음성 데이터는 서버로 전송하지 않는다.
5. Telemetry 이벤트는 **추론 발생 시 즉시 전송된다.**
6. Telemetry 구조는 **Write-heavy 로그 구조**를 따른다.

---

# 1) 서비스 개요

영상통화 중 모바일 디바이스에서 실행되는

On-device AI 모델이 딥페이크 탐지를 수행하고

추론 결과의 운영 지표를 서버로 전송한다.

서버는 해당 데이터를 저장하여

- 모델 안정성 모니터링
- 모델 버전별 성능 비교
- 운영 중 이상 탐지

등의 목적으로 활용한다.

---

# 2) 시스템 구성

모바일 디바이스

역할

- 영상통화 실행
- On-device AI 추론 수행
- Telemetry 이벤트 생성
- 서버로 결과 전송

---

Spring Boot 서버

역할

- Telemetry 이벤트 수신
- Telemetry 데이터 저장
- 운영 모니터링 데이터 제공

---

MySQL

역할

- Telemetry 로그 저장

---

Grafana (추후)

역할

- Telemetry 데이터 시각화
- 모델 운영 상태 모니터링

---

# 3) 데이터 흐름

On-device AI 추론 실행

↓

Telemetry 이벤트 생성

↓

POST /telemetry

↓

Spring Boot 서버

↓

MySQL telemetry_log 저장

↓

Grafana 시각화

---

# 4) API 구조

Telemetry 이벤트 전송 API

POST

/telemetry

모바일에서 **단일 이벤트를 전송한다.**

Batch 전송은 사용하지 않는다.

---

# 5) Request 구조

Header

Content-Type: application/json

---

Body

| 변수명 | 타입 | 필수 여부 | 설명 |
| --- | --- | --- | --- |
| model_type | String | Y | 모델 유형 (FACE / DEEPVOICE) |
| model_version | String | Y | 모델 버전 |
| inference_success | Boolean | Y | 추론 성공 여부 |
| failure_type | String | N | 추론 실패 원인 |
| inference_latency_ms | Integer | Y | 추론 수행 시간 (ms) |
| risk_score | Double | N | 딥페이크 위험도 점수 (0~1) |
| timestamp | String | Y | 추론 발생 시각 (ISO 8601) |
| device_hash | String | Y | 기기 식별 해시 |
| session_id | String | Y | 통화 세션 식별자 |
| device_temperature | Float | N | 디바이스 온도 |

---

failure_type ENUM

MODEL_LOAD_FAIL

TIMEOUT

INFERENCE_ERROR

UNKNOWN

---

# 6) Response 구조

성공

```
{
  "success":true,
  "message":"telemetry received",
  "data": {
    "received":1
  }
}
```

---

오류

```
{
  "code":"INVALID_PAYLOAD",
  "message":"Invalid telemetry request"
}
```

```
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Telemetry processing failed."
}
```

---

# 7) 데이터베이스 설계

Telemetry 데이터는 **단일 로그 테이블**에 저장한다.

table: ai_telemetry_logs

```
CREATE TABLE ai_telemetry_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT,
  model_version VARCHAR(100) NOT NULL,
  model_type VARCHAR(20) NOT NULL,
  inference_success BOOLEAN NOT NULL,
  failure_type VARCHAR(50),
  inference_latency_ms BIGINT NOT NULL,
  risk_score DOUBLE,
  timestamp TIMESTAMP NOT NULL,
  device_hash VARCHAR(255) NOT NULL,
  session_id VARCHAR(255) NOT NULL,
  device_temperature FLOAT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id)
);
```

---

# 8) 인덱스 설계

운영 모니터링 조회를 위해 다음 인덱스를 사용한다.

```
INDEX idx_timestamp
INDEX idx_model_type
INDEX idx_session_id
```

---

# 9) 서버 역할

Spring Boot 서버는 다음 역할만 수행한다.

- Telemetry 이벤트 수신
- Telemetry 데이터 검증
- Telemetry 데이터 저장

다음 작업은 수행하지 않는다.

- 딥페이크 판단
- 모델 추론
- 원본 영상/음성 처리

---

# 10) 서버 프로젝트 구조

Spring Boot 구조 (폴더 구조)

```
com
└── shutter
    └── api
        ├── ShutterApplication.java
        ├── global
        │   ├── auth
        │   ├── common
        │   ├── config
        │   ├── exception
        │   ├── jwt
        │   └── security
        ├── guardian
        │   ├── controller
        │   ├── domain
        │   ├── dto
        │   │   ├── request
        │   │   └── response
        │   ├── repository
        │   └── service
        └── user
            ├── controller
            ├── domain
            ├── dto
            │   ├── request
            │   └── response
            ├── repository
            └── service
```

---

# 11) 향후 확장 가능 항목

Telemetry 분석 확장

추가 가능 필드

- app_version
- os_version
- device_model
- threshold 값