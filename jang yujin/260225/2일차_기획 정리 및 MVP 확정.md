### 📌 프로젝트 정보

| 항목 | 내용 |
| --- | --- |
| **프로젝트명** | (미정) – 실시간 영상통화 지인 사칭 딥페이크 탐지 및 경고 서비스 |
| **기획 배경** |   • AI 기반 딥페이크·딥보이스 기술 고도화로 영상통화·음성 기반 지인 사칭 범죄 급증
     - 글로벌 통계 기준 AI 음성 피싱(Vishing) 442% 증가, 2027년까지 피해액 400억 달러 전망
     - 부모·고령층이 자녀 사칭 음성·영상 요청에 높은 응답률(최대 41%)
     - 성인 4명 중 1명은 AI 음성 사기 경험, 10명 중 1명은 직접 표적 경험
  • 통화 중 실시간 위험 감지 및 선제적 보호 서비스 필요성 확대 |
| **주요 기능** | ① 영상통화 중, 실시간 지인 사칭·딥페이크 위험 판별 (On-device 추론)
② 영상통화 중, 딥페이크일 경우 즉시 딥페이크 감지 알림
        ex_딥페이크 피싱 감지
③ 영상통화 중, 딥페이크일 경우 대화 내용 분석 (On-device 추론)
④ 영상통화 중, 딥페이크 확률 및 대화 분석 기반 위험도 단계별 즉시 알림
        ex_상대에게 계좌번호 및 집주소를 발설하였습니다. 금융 사기가 의심되어 전화를 종료하세요.
⑤ 영상통화 중, 딥페이크일 경우, 금융 앱 실행 차단(보호 모드)
⑥ 영상통화 중, 딥페이크일 경우, 보호자 긴급 알림 시스템
        ex_OOO님이 00시 00분에 xxx-xxxx-xxx 번호로 계좌번호 및 집주소를 발설하였습니다. 피싱이 의심되므로 조치가 필요합니다. |
| **기술 스택** | [FE]
  • Android SDK + Kotlin
  • Jetpack Compose

[BE]
  • 메인 API 서버: Java 21, Spring Boot 3.5.9
  
[DB]
  • MySQL
  • Redis

[Infra]
  • AWS EC2
  • docker, docker compose
  • jenkins
  • nginx

[AI]
  • NDK
  • JNI
  • Pytorch
  • OpenCV
  • QNN/SNPE/ENN |
| **진행 현황** |   • 지인 사칭·딥페이크 범죄 사례 및 통계 자료 조사 완료
  • MVP 범위 설정 완료
  • Android 영상통화 감지 및 온디바이스 ML 추론 기술 타당성 검토 중 |
| **참고 링크** | 피그마 페이지: [https://www.figma.com/design/UUGNJjfKPyjyYzKUIL1xHd/특화-프로토타입?node-id=0-1&t=w4wHjyUk3otn9sgU-1](https://www.figma.com/design/UUGNJjfKPyjyYzKUIL1xHd/%ED%8A%B9%ED%99%94-%ED%94%84%EB%A1%9C%ED%86%A0%ED%83%80%EC%9E%85?node-id=0-1&t=w4wHjyUk3otn9sgU-1)
notion 기획 명세 페이지: [https://www.notion.so/3128f379f3ec803dabebe982dc4bd250?source=copy_link](https://www.notion.so/3128f379f3ec803dabebe982dc4bd250?pvs=21) |