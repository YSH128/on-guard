# Grafana AI Telemetry 모니터링 설정 가이드

작성일: 2026-03-05
작성자: BE팀
브랜치: feat/be/monitoringSetting-S14P21A404-164

---

## 1. 개요

On-device AI 딥페이크 탐지 모델(FACE / DEEPVOICE)의 추론 결과를 MySQL에 수집하고,
Grafana 대시보드를 통해 모델 운영 상태를 시각화하는 모니터링 환경을 구성했습니다.

### 구조

```
Mobile Device (On-device AI)
        │ POST /telemetry
        ▼
Spring Boot Server
        │ Batch Insert (1초마다 최대 100건)
        ▼
MySQL (ai_telemetry_logs 테이블)
        │ Direct SQL Query
        ▼
Grafana Dashboard
```

---

## 2. 로컬 환경에서 구성한 것

### 2-1. docker-compose.yml에 Grafana 서비스 추가

```yaml
grafana:
  image: "grafana/grafana:latest"
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_USER=${GRAFANA_ADMIN_USER:-admin}
    - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-admin}
    - DB_NAME=${DB_NAME}
    - DB_USERNAME=${DB_USERNAME}
    - DB_PASSWORD=${DB_PASSWORD}
  volumes:
    - grafana_data:/var/lib/grafana
    - ./grafana/provisioning/dashboards:/etc/grafana/provisioning/dashboards
  depends_on:
    mysql:
      condition: service_healthy
  networks:
    - shutter-network
  restart: unless-stopped
```

### 2-2. 프로비저닝 파일 구조

```
grafana/
└── provisioning/
    ├── datasources/
    │   └── mysql.yaml         # MySQL datasource 자동 연결 설정 (현재 미사용, 아래 참고)
    └── dashboards/
        ├── dashboards.yaml    # 대시보드 자동 로드 설정
        └── ai_telemetry.json  # AI Telemetry 대시보드 패널 정의
```

### 2-3. 대시보드 구성 (ai_telemetry.json)

| 섹션 | 패널 |
|---|---|
| **Core Stats** | Total Inferences, Success Rate, Failure Rate, Avg/p95/p99 Latency |
| **Time Series** | 추론 수 추이, 성공/실패율 추이, Latency 추이 |
| **Model Analysis** | Model Version별 성공률, Model Type별 Latency |
| **Distribution** | Risk Score 분포, Failure Type 분포 |
| **Temperature** | 기기 온도 추이 (임계값: 35°C 경고, 40°C 위험) |
| **Device Analysis** | 기기별 성능 요약 테이블, 문제 기기 탐지 (실패율 10% 초과) |

- **기기 필터 드롭다운**: 상단에서 특정 device_hash 선택 시 Device Analysis 테이블 필터링
- **AI팀 제공 기준값 반영**: Inference Latency 3.7초, Model Load 6.4초 임계값 적용

### 2-4. 로컬에서 Datasource를 수동 등록한 이유

`grafana/provisioning/datasources/mysql.yaml`을 통해 자동 등록을 시도했으나,
Windows 환경에서 생성된 파일의 CRLF 개행 문자가 Linux 컨테이너 내부에서 YAML 파싱 오류를 일으켜 (`data source not found`)
로컬에서는 Grafana UI 또는 API를 통해 수동 등록했습니다.

**EC2(Linux)에서는 이 문제가 발생하지 않으므로 mysql.yaml 자동 프로비저닝이 정상 동작합니다.**

---

## 3. EC2 배포 시 인프라팀이 해야 할 작업

### 3-1. docker-compose.yml 확인

Grafana 서비스는 이미 추가되어 있습니다. `prod` profile 없이 항상 실행됩니다.
MySQL, Redis와 동일한 `shutter-network`에 연결되어 있습니다.

### 3-2. datasource 자동 프로비저닝 마운트 복원

현재 docker-compose.yml의 Grafana volumes:

```yaml
volumes:
  - grafana_data:/var/lib/grafana
  - ./grafana/provisioning/dashboards:/etc/grafana/provisioning/dashboards
```

EC2에서는 datasources도 마운트하도록 변경해주세요:

```yaml
volumes:
  - grafana_data:/var/lib/grafana
  - ./grafana/provisioning:/etc/grafana/provisioning
```

이렇게 하면 Grafana 시작 시 MySQL datasource가 자동으로 등록됩니다.

### 3-3. .env에 Grafana 계정 추가

EC2의 `.env` 파일에 아래 항목을 추가해주세요:

```
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=원하는_비밀번호
```

보안을 위해 기본값 `admin`은 반드시 변경해주세요.

### 3-4. mysql.yaml의 환경변수 확인

`grafana/provisioning/datasources/mysql.yaml`은 아래 환경변수를 사용합니다:

```
DB_NAME       → Grafana가 접속할 DB 이름
DB_USERNAME   → MySQL 유저명
DB_PASSWORD   → MySQL 비밀번호
```

이 값들은 docker-compose.yml의 Grafana service `environment` 블록에서 주입되므로
`.env` 파일에 올바르게 설정되어 있으면 자동으로 적용됩니다.

### 3-5. 포트 및 접근

- Grafana 기본 포트: **3000**
- EC2 보안 그룹에서 **3000 포트 인바운드 허용** 필요
- 또는 Nginx reverse proxy를 통해 `/grafana` 경로로 노출 권장

---

## 4. 주의사항

### MySQL 연결 주소

Grafana는 Docker 내부 네트워크에서 실행되므로 MySQL 접속 주소는 반드시:

```
mysql:3306   ✅ (Docker 내부 hostname)
localhost:3306   ❌
localhost:3307   ❌
```

### grafana_data 볼륨

Grafana 볼륨(`grafana_data`)에는 수동으로 추가한 대시보드, 알림 설정, 플러그인 등이 저장됩니다.
`docker compose down -v` 실행 시 **데이터가 삭제**되므로 주의해주세요.

로컬에서 수동 등록한 datasource UID(`aff3w4y7zad4wd`)는 EC2에서 자동 프로비저닝으로 등록되면
`shutter-mysql`로 통일됩니다. 대시보드 JSON은 이미 `shutter-mysql` UID를 참조하도록 준비되어 있습니다.

> **로컬 한정 이슈**: 현재 로컬의 `ai_telemetry.json`에는 datasource UID가 `aff3w4y7zad4wd`로 하드코딩되어 있습니다.
> EC2 배포 전에 아래 명령으로 원래대로 되돌려주세요:
>
> ```bash
> sed -i 's/aff3w4y7zad4wd/shutter-mysql/g' grafana/provisioning/dashboards/ai_telemetry.json
> ```

### 대시보드 자동 로드

`grafana/provisioning/dashboards/dashboards.yaml` 설정으로
Grafana 시작 시 `ai_telemetry.json`이 자동으로 로드됩니다.
Grafana UI에서 대시보드를 수정할 수 있지만, 컨테이너 재시작 시 JSON 파일 기준으로 초기화됩니다.
대시보드를 영구 수정하려면 JSON 파일을 직접 수정해주세요.

---

## 5. EC2 배포 후 확인 방법

```bash
# 1. 서비스 확인
docker compose ps

# 2. Grafana 로그 확인
docker compose logs grafana

# 3. 브라우저에서 접속
http://<EC2_PUBLIC_IP>:3000

# 4. 로그인 후 Connections → Data sources에서 "Shutter MySQL" 자동 등록 확인
# 5. Dashboards → "AI Telemetry Monitoring" 대시보드 확인
```

---

## 6. 참고 파일 목록

| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | Grafana 서비스 정의 |
| `grafana/provisioning/datasources/mysql.yaml` | MySQL datasource 자동 등록 (EC2용) |
| `grafana/provisioning/dashboards/dashboards.yaml` | 대시보드 자동 로드 설정 |
| `grafana/provisioning/dashboards/ai_telemetry.json` | AI Telemetry 대시보드 패널 및 쿼리 |
| `.env` | GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD 설정 |