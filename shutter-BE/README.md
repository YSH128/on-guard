# OnGuard - Backend & Infrastructure

**OnGuard** 프로젝트의 백엔드 시스템 및 인프라스트럭처에 대한 가이드 문서입니다.

## 1. Background (프로젝트 배경 및 목표)

### 문제 상황
최근 AI 기술을 악용한 **딥페이크(Deepfake) 화상통화 범죄 및 고도화된 보이스피싱**이 급증하고 있습니다. 이러한 공격은 특히 디지털 취약계층에게 심각한 금전적/정신적 피해를 유발하지만, 사후 대처에 불과한 기존 솔루션으로는 범죄 피해를 적시에 막을 골든타임 확보에 한계가 있었습니다.

### 해결 목표
* **실시간 AI 탐지와 통신망 결합**: 사용자의 화상 통화 중 서버의 AI 모델을 활용해 딥페이크 및 피싱 의심 패턴을 실시간으로 감지.
* **보호자 연동 기반 골든타임 확보**: 범죄 의심 상황 감지 시, 사전에 등록된 **보호자에게 즉각적인 모바일 푸시 알림(FCM)**을 전송하고, **초단위 실시간 위치(GPS) 정보**를 공유하여 신속하게 범죄 시도를 물리적, 시간적으로 차단.

---

## 2. Key Features & System Architecture

### 2.1 Key Features (주요 기능)
* **보호자-피보호자 다대다 매핑**: 카카오 로그인을 통한 간편 인증 및 가족 단위 등 복잡한 보호자-피보호자(Ward-Guardian) 관계를 지원
* **WebRTC 기반 화상 통화 및 세션 제어**: Agora RTC 서비스를 연동한 안정적인 화상 통신 지원. **Redis 기반의 분산 락(Lock)**을 활용하여 중복 발신 및 동시성 충돌을 방어
* **초저지연 실시간 GPS 공유 (WebSocket + Redis)**
    * 일시적인 성격을 띄는 피보호자의 GPS 좌표는 MySQL 대신 **Redis**에 저장(60초 TTL 설정)하여 대용량 I/O 병목을 제거
    * WebSocket 채널을 통해 보호자 화면의 지도에 좌표를 끊김 없이 브로드캐스팅
* **AI 백그라운드 이벤트 처리 및 자동 알림**
    * AI가 도출한 리스크 점수를 백엔드가 비동기 수신
    * 카카오-FCM 서버와 연동하여 대상 보호자의 애플리케이션으로 긴급 푸시 알림 비동기(Async) 전송

### 2.2 System Architecture (배포 아키텍처 및 시스템 구조)
응답 속도와 부하 분산을 고려한 아키텍처 특징입니다.

* **Main Application**: `Spring Boot 3.5.11 (Java 21)`
* **Database & Caching**:
    * **MySQL**: 유저 식별, 보호자 관계, 모바일 알림 이력, `ai_telemetry_logs` 등 영구 보존이 필요한 핵심 데이터 관리
    * **Redis**: GPS 트래픽 처리, JWT Refresh Token 캐싱, 화상 채널 입장 상태 관리
* **배포 아키텍처 (CI/CD 파이프라인)**:
    * Github Webhook과 **Jenkins 파이프라인**을 연동하여 코드 Push 시 자동화 빌드/테스트 수행
    * 빌드된 이미지를 `Docker-Compose` 기반으로 WAS 및 모니터링 컨테이너 등과 함께 통합 릴리즈(무중단 배포 지향)
    * `Grafana`와 `Logstash`를 연동해 서버 리소스 및 내부 API 로깅에 대한 모니터링 환경 구축

### 2.3 Project Structure (주요 폴더 구조)
백엔드 도메인 주도 설계(DDD) 패턴 패키지 구조입니다.
```text
/shutter-BE
├── src/main/java/com/shutter/api/
│   ├── global/         # JWT 토큰 관리, Config(Redis, Security) 공통 설정 
│   ├── user/           # 로그인, 보호자-피보호자 관계 연결 서비스
│   ├── rtc/            # 화상 통화 방 개설 로직(Agora 연동) 및 세션 제어
│   ├── gps/            # 실시간 WebSocket 위치 브로드캐스팅
│   └── notification/   # AI 탐지 이벤트 리스너 및 FCM 푸시 알림 전송부
├── build.gradle        # 의존성 라이브러리 및 빌드 상세
├── Dockerfile          # Spring Boot Application 컨테이너 이미지화 스크립트
├── Jenkinsfile         # Jenkins CI/CD 배포 파이프라인 선언부
└── docker-compose.yml  # 운영/스테이징 배포 인프라 오케스트레이션 구성

---

## 3. Setup
### 3.1 Dependencies
- **Core**: Spring Boot 3.5.11, Java 21 LTS
- **Data**: Spring Data JPA (MySQL), Spring Data Redis
- **Security**: Spring Security, OAuth2-Client, jjwt(0.12.6)
- **Web & Realtime**: Spring WebFlux, Spring WebSocket
- **Third Party**: io.agora:authentication:2.0.0
- **Docs & Monitoring**: springdoc-openapi-starter-webmvc-ui, logstash-logback-encoder

### 3.2 환경 변수 세팅 (.env)
애플리케이션 구동을 위해 루트 디렉토리(`./shutter-BE/.env`)에 아래의 환경 변수 설정이 필요합니다. 상세 템플릿은 `.env.example`을 참고하세요.

```env
# Database & Redis
DB_HOST=localhost
DB_PORT=3307
DB_NAME=shutter
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password

REDIS_HOST=localhost
REDIS_PORT=6379

# Kakao & JWT
KAKAO_REST_API_KEY=your_kakao_rest_api_key
JWT_SECRET=your_jwt_secret_key_at_least_32_chars

# Monitoring (Optional)
GRAFANA_ADMIN_PASSWORD=your_password
```

---

## 4. Usage
로컬 환경에서 백엔드 서버를 구동하고 테스트하는 방법입니다.

### 4.1 인프라 컨테이너 구동
Docker Compose를 활용해 백그라운드에서 로컬 테스트용 MySQL/Redis 인프라를 구축합니다.
```bash
cd shutter-BE
docker-compose -f docker-compose.local.yml up -d
```

### 4.2 애플리케이션 실행
Gradle Wrapper를 사용하여 Spring Boot 환경을 빌드하고 실행합니다.
```bash
./gradlew bootRun
```

### 4.3 API 자동화 명세 확인
서버 구동 후 브라우저를 통해 아래 주소로 접속하면 Swagger UI를 통해 모든 엔드포인트를 테스트할 수 있습니다.
- **Swagger URL**: `http://localhost:8080/swagger-ui.html`

---

## 5. Contributors

- **윤상현 (Backend & Infrastructure)**
    - **Backend Architecture & API**
        - 카카오 소셜 로그인 연동 및 Spring Security + JWT 기반 인증/인가 체계 구축
        - 복잡한 보호자-피보호자 N:M 관계의 MySQL DB 스키마 설계 및 쿼리 최적화
        - Agora RTC를 활용한 화상 통화방 접속/세션 제어 로직 구현
        - WebSocket(STOMP)을 이용한 양방향 실시간 통신망 구축
        - 비동기(Async) 카카오 푸시 알림(FCM) 레이어 개발
    - **Performance & Concurrency (Redis)**
        - Redis 분산 락(Lock) 메커니즘을 적용하여 중복 발신 및 화상 세션 동시성 충돌 해결
        - Redis In-Memory 캐싱 및 TTL 기능을 활용하여 초단위 GPS 고속 연산 처리 및 I/O 병목 해소
    - **Infrastructure & CI/CD**
        - 프로젝트 단위 Docker 컨테이너화 및 Docker-Compose 기반 인프라 배포 환경 이식성 증대
        - Jenkins를 활용한 빌드 자동화 및 CI/CD 무중단 서버 배포 파이프라인 설계

- **박수민**
    - **Backend Architecture & API**
        -
    - **Infrastructure**
        -