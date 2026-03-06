# AI Telemetry Monitoring Server Context

작성일: 2026-03-05

목적: Claude Code가 프로젝트의 현재 상태와 향후 작업을 이해할 수 있도록 AI 모니터링 서버의 설계, 구현 현황, 향후 작업을 정리한다.

---

# 1. 프로젝트 개요

본 서버는 **On-device AI 딥페이크 탐지 모델(FACE / DEEPVOICE)의 Telemetry 데이터를 수집하여 모델 운영 상태를 모니터링하는 서버**이다.

중요한 설계 원칙:

- AI 모델 추론은 **모바일 디바이스에서 수행된다.**
- 서버는 **AI 추론을 수행하지 않는다.**
- 서버의 역할은 **Telemetry 데이터 수집 및 저장**이다.
- 원본 영상 및 음성 데이터는 서버로 전송되지 않는다.

---

# 2. 시스템 아키텍처

```
Mobile Device (On-device AI)
        │
        │ Telemetry Event
        ▼
Spring Boot Server
(Telemetry API)
        │
        ▼
MySQL Database
(ai_telemetry_logs)
        │
        ▼
Grafana
(AI Monitoring Dashboard)
```

---

# 3. Telemetry API

모바일 디바이스는 **AI 추론 발생 시 Telemetry 이벤트를 서버로 전송한다.**

Endpoint

```
POST /telemetry
```

Request Example

```
{
  "model_type": "DEEPVOICE",
  "model_version": "v1.0.0",
  "inference_success": true,
  "inference_latency_ms": 120,
  "risk_score": 0.15,
  "timestamp": "2026-03-04T12:00:00+09:00",
  "device_hash": "tttt123hash",
  "session_id": "session-uuid-001",
  "device_temperature": 38.5
}
```

---

# 4. Database

Telemetry 데이터는 단일 로그 테이블에 저장된다.

table

```
ai_telemetry_logs
```

주요 필드

| column | 설명 |
| --- | --- |
| model_type | FACE / DEEPVOICE |
| model_version | AI 모델 버전 |
| inference_success | 추론 성공 여부 |
| inference_latency_ms | 추론 latency |
| risk_score | 딥페이크 위험도 |
| timestamp | 추론 발생 시각 |
| device_hash | 디바이스 식별자 |
| session_id | 통화 세션 ID |
| device_temperature | 디바이스 온도 |

---

# 5. 현재 구현 완료된 기능

다음 기능은 이미 구현 완료되었다.

Telemetry API

```
feat: Telemetry 수집 API 구현
```

Telemetry DB 저장

```
feat: Telemetry DB 저장 로직 구현
```

Batch Insert

```
feat: Batch insert 구현
```

AI Monitoring Metrics 문서

```
docs: AI 모니터링 지표 정의 문서 작성
```

AI Anomaly Detection 기준

```
docs: AI 성능 이상 징후 감지 기준 정의
```

---

# 6. AI Monitoring Metrics

Telemetry 데이터를 기반으로 다음 지표를 모니터링한다.

핵심 지표

- Inference Count
- Success Rate
- Failure Rate
- Average Latency
- p95 Latency
- p99 Latency

분석 지표

- Model Version Success Rate
- Model Type Latency
- Device Temperature
- Risk Score Distribution

---

# 7. AI 모델 운영 기준 (AI 팀 제공)

### Latency

| 항목 | 기준 |
| --- | --- |
| Model Load Latency | 약 6.4초 |
| Inference Latency | 약 3.7초 |

---

### 실패율 기준

Face 모델

```
30초 동안 90% 이상 감지 성공
```

Voice 모델

```
10초 동안 90% 이상 감지 성공
```

---

### Risk Score Threshold

| 모델 | threshold |
| --- | --- |
| FACE | 0.7 |
| VOICE | 0.8 |

---

### Inference 호출 빈도

| 모델 | 호출 주기 |
| --- | --- |
| FACE | 1초당 2회 |
| VOICE | 4초당 1회 |

---

### 디바이스 온도 기준

| 상태 | 정상 범위 |
| --- | --- |
| 대기 상태 | 25°C ~ 35°C |
| 배터리 적정 온도 | 0°C ~ 35°C |

---

# 8. Monitoring 방식

Grafana가 **MySQL을 직접 조회하여 Telemetry 데이터를 시각화한다.**

구조

```
Grafana → MySQL Query
```

즉

- Monitoring API는 필요하지 않다.
- Grafana SQL Query로 지표를 계산한다.

---

# 9. Grafana Dashboard (계획)

다음 패널을 구성할 예정이다.

Core Monitoring

- Inference Count
- Success Rate
- Failure Rate
- Average Latency
- p95 Latency
- p99 Latency

AI Analysis

- Model Version Success Rate
- Model Type Latency
- Device Temperature
- Risk Score Distribution

---

# 10. 앞으로 해야 할 작업

다음 작업이 필요하다.

Grafana 연동

```
MySQL datasource 연결
```

Dashboard 구성

```
AI Telemetry Dashboard 생성
```

Monitoring Query 작성

예시

```
latency
success rate
model version metrics
```

Alert 기준 설정

```
failure rate 증가
latency spike
temperature 상승
```

---

# 11. Claude Code가 도와줄 수 있는 작업

Claude Code는 다음 작업을 지원할 수 있다.

- Telemetry Repository 코드 개선
- Batch Insert 최적화
- Grafana SQL Query 작성
- Monitoring Dashboard 설계
- AI Telemetry 분석 쿼리 작성