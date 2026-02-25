# Git Convention

본 문서는 프로젝트에서 사용하는 Git 브랜치 및 커밋 메시지 컨벤션을 정의합니다.

---

## 1. Branch Convention

### 1.1 Main Branch
main : 최종 결과물 및 배포 브랜치
dev : 개발 통합 브랜치

### 1.2 Working Branch
[작업] type/domain/feat-name
[이슈] type/domain/feat-name-티켓번호

#### 추천 type 리스트:
| Type | Description |
|---|---|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 기능 변경 없는 코드 개선 |
| docs | 문서 수정 |
| style | 코드 포맷 수정 (공백, 세미콜론 등) |
| test | 테스트 코드 |
| chore | 설정, 빌드, 기타 작업 |

#### 추천 domain 리스트:
- fe: 프론트엔드 작업

- be : 백엔드 작업

- ai: 모델 트레이닝, 추론 로직 (얼굴 인식 등)

- embedded: 하드웨어 제어, 센서 관련

- api: 백엔드 통신 및 데이터 처리

#### Example
[작업] feat/web/dashboard-layout

[작업] feat/ai/face-embedding

[작업] fix/embedded/motor-error

[작업] docs/api/model-accuracy

[작업] chore/project-setup

[이슈] feat/signup-api-S14P11A404-28

#### Rules
- 소문자 사용
- 단어 구분은 하이픈(`-`) 사용
- 한글 사용 금지!!!
- 기능 단위로 브랜치 생성

---

## 2. Commit Message Convention

### 2.1 Format
type: subject

### 2.2 Type List
| Type | Description |
|---|---|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 기능 변경 없는 코드 개선 |
| docs | 문서 수정 |
| style | 코드 포맷 수정 (공백, 세미콜론 등) |
| test | 테스트 코드 |
| chore | 설정, 빌드, 기타 작업 |
| submit | 개인 학습 과제 제출 |

---

### 2.3 Subject Rules     
- 현재형 사용
- 50자 이내 권장
- 마침표 사용 금지
- 무엇을 했는지 명확히 작성

#### Example
feat: add user position based monitor tracking

fix: correct robot arm angle calculation

refactor: separate monitor control logic

docs: update project README