# 📌 기본 정보

- **날짜:** 2026-02-24
- **강연자:** 이승유 강사님
- **주제:** 합성 데이터 생성, 지식 증류 기반 모델 튜닝, 모델 경량화, vLLM 서빙 최적화까지 end-to-end 실습 중심 특강

---

# 🎯 핵심 요약

- LLM 기반 합성 데이터 생성과 Knowledge Distillation을 중심으로, 모델 경량화와 vLLM 서빙 최적화까지 이어지는 생성형 AI의 end-to-end 실습 및 이론 통합 특강.

---

# 🔊 특강 - 실습

### ✅ knowledge distillation을 위한 LLM기반 Pseudo Labeled 데이터 생성

- 학습 자료 : https://lab.ssafy.com/instruction/14th/skeleton-projects/ai-special-skeleton
1. 사전요건: `.env`에 `UPSTAGE_API_KEY` 설정, `dotenv`, `openai`, `pandas` 등 사용.
2. 환경변수 로드: `.env` 존재 확인 후 `load_dotenv`로 키 불러오고 없으면 에러 발생.
3. API 클라이언트 설정: `OpenAI(api_key=..., base_url="<https://api.upstage.ai/v1>")`로 Upstage 연결.
4. 기본 챗 함수 정의: `chat_completion`(model, messages)로 응답 텍스트 및 reasoning(내부사고) 획득.
5. 데이터 로드: `data/train.csv`를 읽어 일부 레코드(예: 상위 10개)로 실험.
6. 프롬프트 엔지니어링: 역할·목표·제약·예시 포함, 출력 포맷으로 JSON 구조 지정 및 파싱 함수 구현.
7. Structured output 사용: `response_format`에 JSON schema 전달해 안정적 JSON 응답을 받고 `json.loads`로 변환.
8. Reasoning 처리: `reasoning_effort="high"`와 스트리밍으로 `delta.reasoning`(CoT)과 `delta.content`(정답) 분리 처리.
9. Pseudo labeling + 평가: 질문별로 답변·reasoning 수집 후 LLM as a Judge로 채점하여 `labeled_data_with_judge.json`로 저장.

⇒ Temperature 같은 변수값들 조정하며 원하는 값 도출하는 연습

---

# 🔊 특강 - 이론

### ✅ 기존 Sequence 모델

- **RNN**
    - 순환 구조로 시퀀스 처리
    - 이전 hidden state → 다음 step 전달
    - 긴 문장 학습에 한계 : 기울기 소실/폭발 + 장기 의존성 문제
- **LSTM**
    - 입력/망각/출력 게이트 사용
    - RNN 개선 버전 : 장기 의존성 문제 해결
- **GRU**
    - LSTM 단순화 버전
    - 게이트 수 감소 → 계산 효율 ↑

### ✅ Attention

- Query, Key, Value구조
- 단어 간 **연관성 가중치 계산 →** 멀리 떨어진 단어 관계도 직접 연결 가능
- Self-Attention: 같은 문장 내부 관계 학습

### ✅ Multi-Head Attention

- 여러 개의 Attention을 병렬로 학습
- 각 head가 다른 관점에 집중 → 다양한 문맥 해석 가능
    - 문장 타입
    - 명사
    - 관계
    - 강조 등

### ✅ Transformer 구조

- RNN 없이 Attention만 사용
- Encoder / Decoder 구조
- 병렬 처리 가능 → 속도 빠름
- 활용
    - **NLP**: GPT(Decoder), BERT(Encoder)
    - **Vision**: ViT (이미지도 패치 단위로 처리)

### ✅ 기존 ML vs 생성형 AI

| 구분 | 기존 ML (Traditional ML) | 생성형 AI (Generative AI) |
| --- | --- | --- |
| 목적 | 분류 / 예측 | 생성 / 복원 / 변환 |
| 학습 대상 | 입력 → 정답 매핑 | 데이터의 확률 분포 |
| 출력 형태 | 라벨, 점수, 확률 | 텍스트, 이미지, 음성 등 새로운 데이터 |
| 내부 구조 | 특징 추출 후 판별 | Latent 공간 학습 후 샘플링 |
| 예시 | 스팸 분류, 숫자 인식 | GPT, 이미지 생성 모델 |

### ✅ Autoencoding vs Autoregressive

- **Autoencoding (AE 계열, BERT류)** - 이해 중심 모델
    - 주로 **인코더 구조**
    - 입력 전체 문맥을 양방향으로 이해
    - 마스킹된 토큰 복원 (재구성 오차 최소화)
    - 문장 이해, 분류, 임베딩에 강점
- **Autoregressive (AR 계열, GPT류)** - 생성 중심 모델
    - 주로 **디코더 구조**
    - 이전 토큰 기반 다음 토큰 예측
    - 순차 생성 방식
    - 텍스트 생성, 대화, 코딩에 강점

### ✅ LLM 등장 흐름 정리

1.  **Transformer 기반 출발**
    - **Transformer (Google) :** LLM의 기본 구조 (Attention)
    - **GPT-1 (OpenAI) :** 디코더 기반 생성 모델
    - **BERT (Google) :** 인코더 기반, 양방향 이해 모델
    - **GPT-2 :** 대규모 사전학습, Zero-shot 가능
2. **AR/AE 결합 시도**
    - **XLNet :** AR + AE 장점 결합
    - **RoBERTa :** BERT 개선판 (훈련 최적화)
    - **MASS (Microsoft) :** Encoder-Decoder 기반 사전학습
    - **BART (Facebook) :** 입력 변형 후 복원 학습 (AE+AR 혼합)
3. **대규모 멀티태스크 시대로**
    - **MT-DNN (Microsoft) :** 여러 NLP task 동시 학습
    - **T5 (Google) :** 모든 NLP를 Text-to-Text로 통일
    - **GPT-3 (OpenAI) :** 175B 파라미터, Zero-shot/Few-shot 본격화

### ✅ 주요 생성형 모델 소개

- **ChatGPT**


    | 구분 | 기간 | 핵심 특징 | 기술적 의미 |
    | --- | --- | --- | --- |
    | **GPT-1 ~ GPT-3** | 2018~2020 | Transformer 기반 대규모 사전학습 정립, few-shot 학습으로 범용 생성 능력 입증 | NLP 연구 패러다임 변화 |
    | **GPT-3.5 ~ GPT-4** | 2021~2023 | RLHF(인간 피드백 기반 강화학습) 적용, 대화 정밀도 향상, 코딩·수학·논리 성능 강화 | 실무 활용 가능 수준 도달 |
    | **GPT-4o (omni)** | 2024 | 텍스트·음성·이미지를 단일 모델에서 처리하는 멀티모달 구현, 실시간 저지연 아키텍처 | 통합 멀티모달 모델 본격화 |
    | **o-시리즈 (o1)** | 2024년 9월 공개 | 내부 추론 시간 확장, Chain-of-Thought 내재화, 복잡 추론·코드·수학 성능 향상 | Reasoning 특화 모델 |
    | **o1-mini / o3-mini** | 2024 | 추론 성능 유지하면서 파라미터 축소, 비용·지연 감소 | 경량 고효율 추론 모델 |
- **Claude**


    | 모델 | 출시일 | 핵심 특징 | 기술적 의미 |
    | --- | --- | --- | --- |
    | **Claude 1** | 2023.3 | 첫 챗봇·API 모델, Helpful-Honest-Harmless 원칙, Constitutional AI 적용 | 안전성 중심 LLM 차별화 |
    | **Claude 2** | 2023.7.11 | 컨텍스트 100K 토큰, Bar 76.5%·HumanEval 71.2% | 대규모 문맥 처리 + 성능 향상 |
    | **Claude 2.1** | 2023.12.6 | 200K 토큰 창, 장문 검색 최적화, 오답률 30% 감소 | 장문 처리 안정성 강화 |
    | **Claude 3 (Haiku / Sonnet / Opus)** | 2024.3.4 | 멀티모달(텍스트+비전), 최대 1M 토큰 실험, Opus 주요 벤치마크에서 GPT-4 계열 능가 발표, refusal 감소 | 고성능 + 대규모 컨텍스트 + 시각 이해 |
    | **Claude 3.5 Sonnet** | 2024.6.21 | Opus보다 빠르고 지능 향상, Vision·코딩·GPQA 성능 강화, 실시간 생성물 편집(Artifacts) | 실무·코딩 특화 모델 |
    | **Claude 4 (Opus 4, Sonnet 4)** | 2025.5.22 | Hybrid Mode(즉시 응답 ↔ 심층 사고 전환), SWE-bench Verified 72.5%, 장기 메모리·병렬 툴·자율 작업 강화 | 에이전트형 고도화 모델 |
- **Deepseek**

### ✅ 생성형 모델 한계점

- **Character Indexing**
    - 철자 세기, 문자 위치 찾기, 글자 개수 세기 등 정밀한 문자 작업에서 할루시네이션 발생
    - LLM : 문자 단위 X 토큰 단위 처리 O → 토크나이징 과정에서 위치 인식 깨짐
- **Fact Conflict (사실 충돌)**
    - 모델 내부 지식끼리 충돌 → 서로 다른 정보가 섞여 잘못된 답 생성
    - 비슷한 인물/연도/관계에서 자주 발생
- **Factual Hallucination (사실 환각)**
    - 존재하지 않는 수치나 정보 생성
    - 연도·통계·숫자에서 자주 발생

### ✅ LLM 작동방식 및 원리

- 텍스트 → 토큰화 → 벡터화(+위치) → Attention 계산 → 확률 분포 생성 → 확률 기반 단어 선택

| 단계 | 설명 | 작동 방식 | 핵심 원리 |
| --- | --- | --- | --- |
| **1. Input** | 텍스트 입력 | 사용자 문장 수집 | 문자열 형태 |
| **2. Tokenize** | 문장을 토큰으로 분해 | BPE, SentencePiece 등 서브워드 분할 | 단어를 작은 단위로 쪼개 어휘 효율화 |
| **3. Token Embedding** | 토큰 → 벡터 변환 | 임베딩 테이블에서 lookup | 의미를 고정 차원 벡터로 표현 |
| **4. Encoding** | 위치 정보 추가 | 토큰 벡터에 위치 벡터 더함 | Transformer는 순서 인식 불가 → 위치 보강 |
| **5. Transformer (LLM)** | 문맥 이해 및 계산 | Self-Attention + MLP 반복 | 단어 간 관계 가중합 계산 |
| **6. Output (Logits)**
⭐⭐⭐ | 다음 토큰 점수 계산 | 전체 vocab에 대한 확률 점수 출력 | Softmax 전 단계 값 |
| **7. Sampling / Decoding** | 실제 단어 선택 | Greedy, Top-k, Top-p 등 | 확률 기반 선택 전략 |

### ✅ Tokenizer: Byte Pair Encoding의 원리

- **초기화**
    - 모든 단어를 문자 단위로 분해
    - 예: `lower → ['l','o','w','e','r']`
    - 초기 어휘는 문자 집합으로 구성
- **빈도 계산**
    - 인접한 문자 쌍(pair)의 출현 빈도 계산
    - 예: `(l,o), (o,w), (w,e), (e,r)`
    - 전체 코퍼스 기준으로 가장 많이 등장한 쌍 탐색
- **쌍 병합**
    - 가장 빈번한 문자쌍을 하나의 토큰으로 병합
    - 예: `(l,o) → 'lo'`
    - `lower → ['lo','w','e','r']`
- **반복**
    - 빈도 계산 → 병합 과정 반복
    - 점점 더 긴 서브워드(subword) 생성
    - 충분히 반복하면 단어가 의미 단위로 분해됨

### ✅ Decoding 전략

| 방식 | 작동 방식 | 특징 | 장점 | 단점 | 주 사용 사례 |
| --- | --- | --- | --- | --- | --- |
| **Greedy Search** | 매 단계에서 확률이 가장 높은 토큰 1개 선택 (`argmax`) | 결정적(항상 동일 결과) | 빠름, 구현 간단 | 반복 발생, 다양성 낮음 | 코드 생성, 단순 응답 |
| **Beam Search** | 상위 k개 후보 유지 후 전체 문장 확률 최대 경로 선택 | 문장 전체 확률 고려 | Greedy보다 품질 안정적 | 계산량 증가, 다양성 낮음 | 번역, 요약 |
| **Sampling** | 확률 분포에서 랜덤 선택 | 비결정적 | 다양성 높음 | 불안정 가능 | 대화형 모델 |
| **Top-k Sampling** | 상위 k개 토큰만 남기고 샘플링 | 저확률 토큰 제거 | 안정성 + 다양성 균형 | k 설정 필요 | 일반 텍스트 생성 |
| **Top-p (Nucleus)** | 누적 확률 p까지 토큰 유지 후 샘플링 | 동적 후보 선택 | 자연스러움, 현재 표준 방식 | p 값 튜닝 필요 | 챗봇, 스토리 생성 |
| **Min-p Sampling** | 개별 토큰 확률이 일정 임계값 이상인 토큰만 유지 후 샘플링 | 절대 확률 기준 필터링 | 극단적 저확률 토큰 제거에 효과적 | 분포 적응성 낮음, 임계값 민감 | 실험적 생성, 안정성 제어 |

### ✅ Temperature 조절

| 구분 | 분포 변화 | 모델 성향 | 언제 사용 |
| --- | --- | --- | --- |
| **T ↓ (0에 가까움)** | 확률 분포가 날카로워짐 (상위 토큰 확률 집중) | 보수적, 결정적, 반복 적음 | 정확성 중요할 때 |
| **T ↑** | 확률 분포가 평탄해짐 (여러 토큰 확률 유사) | 창의적, 다양성 증가, 예측 불가성 ↑ | 아이디어·스토리 생성 |

### ✅ Chain of Thought (CoT)의 개념

- **정의 :** LLM이 최종 답변 전에 중간 추론 과정을 단계적으로 생성하도록 유도하는 기법
- **필요성 :** 중간 단계 표현이 최종 답의 신뢰도 향상에 기여

  → 다단계 계산·논리 문제에서 특히 효과적

- **작동 방식**
    - 문제 → 중간 논리 단계 생성 → 최종 결론 도출
    - “Let’s think step by step” 같은 프롬프트로 유도 가능
- **주의점**
    - 복잡한 문제에서 짧은 답만 요구하면 부정확한 결과 발생 가능
    - 특히 수학·논리 추론 과제에서 이러한 현상이 두드러짐

---

# 🧠 인사이트

- **내가 새롭게 알게 된 점**
    - **클로드 토큰이 빨리 닳는 이유 = ‘한국어 번역’ :** 한국어 토큰 해석 시, 한 글자를 네 가지 토큰을 활용 → 영어 번역 후 사용 good
    - 학습되지 않은 단어(OOV)에 대해선 **byte 단위로 토크나이징**     ex) 이모지 → 4개 토큰
- **추가 조사 및 공부**
    - **vision transformer 한계 :** 불확실성 표현·공격 내성·공정성 측면에서 구조적 한계

  | 문제 | 설명 | 원인 | 본질 |
      | --- | --- | --- | --- |
  | Overconfidence | 틀린 예측을 하면서도 높은 확률(softmax 값)을 출력하는 현상 | Softmax 구조 | 확률 해석 문제 |
  | Adversarial | 사람이 보기엔 거의 동일한 이미지인데 **아주 작은 노이즈만 추가해도 완전히 다른 예측** | 고차원 선형 경계 | 결정 경계 취약 |
  | 데이터 편향 | • 학습 데이터 분포가 한쪽으로 치우쳐 있음
    • 특정 클래스/환경에 대해 과도하게 학습됨 | 데이터 분포 왜곡 | 상관관계 학습 |
    - 학습되지 않은 이미지 데이터(OOD) → **Multi-scale 처리** (여러 해상도로 특징 추출)