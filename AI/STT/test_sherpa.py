import sherpa_onnx
import sys
import numpy as np
import torchaudio
import httpx
import os
from dotenv import load_dotenv

load_dotenv()

def analyze_text_with_gms(text):
    if not text.strip():
        return "변환된 텍스트가 없어 분석할 수 없습니다."
        
    url = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions"
    token = os.environ.get("GMS_API_TOKEN")
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }
    
    system_prompt = (
        "주어진 대화(STT 결과)의 내용을 분석하여 주된 의도 딱 하나만 단답형으로 출력해주세요.\n"
        "다음 옵션 중 하나만 선택하세요: '계좌 이체 요구', '특정 장소 유인', '개인정보 요구', '일상 대화', '기타'\n"
        "설명이나 부가적인 문구, 마침표 없이 오직 선택된 문자열 하나만 정확히 출력하세요."
    )
    
    data = {
        "model": "gpt-5-mini",
        "messages": [
            {
                "role": "developer",
                "content": system_prompt
            },
            {
                "role": "user",
                "content": text
            }
        ]
    }
    
    try:
        with httpx.Client(timeout=30.0) as client:
            response = client.post(url, headers=headers, json=data)
            response.raise_for_status()
            result = response.json()
            return result['choices'][0]['message']['content']
    except Exception as e:
        return f"[오류] GMS API 요청 실패: {e}"

def create_recognizer():
    encoder = "./sherpa-onnx-zipformer-korean-2024-06-24/encoder-epoch-99-avg-1.onnx"
    decoder = "./sherpa-onnx-zipformer-korean-2024-06-24/decoder-epoch-99-avg-1.onnx"
    joiner = "./sherpa-onnx-zipformer-korean-2024-06-24/joiner-epoch-99-avg-1.onnx"
    tokens = "./sherpa-onnx-zipformer-korean-2024-06-24/tokens.txt"

    recognizer = sherpa_onnx.OfflineRecognizer.from_transducer(
        encoder=encoder,
        decoder=decoder,
        joiner=joiner,
        tokens=tokens,
        num_threads=1,
        sample_rate=16000,
        feature_dim=80,
        decoding_method="greedy_search",
    )
    return recognizer

def main():
    wave_filename = "./fakeVideo.wav"

    print("Loading recognizer...")
    recognizer = create_recognizer()

    print(f"Loading and preprocessing audio: {wave_filename}")
    try:
        waveform, sample_rate = torchaudio.load(wave_filename)
        
        # Convert stereo to mono
        if waveform.shape[0] > 1:
            waveform = waveform.mean(dim=0, keepdim=True)
            
        # Resample to 16kHz
        if sample_rate != 16000:
            waveform = torchaudio.functional.resample(waveform, orig_freq=sample_rate, new_freq=16000)
        
        # Extract samples as float32
        samples = waveform.squeeze(0).numpy().astype(np.float32)

        print("Recognizing...")
        stream = recognizer.create_stream()
        stream.accept_waveform(16000, samples)
        recognizer.decode_stream(stream)

        print(f"Transcription: {stream.result.text}")
        
        print("\n[AI 분석 진행 중...]")
        analysis_result = analyze_text_with_gms(stream.result.text)
        print(f"\n[분석 완료] 최종 주된 의도: {analysis_result}")
    except Exception as e:
        print(f"Error processing audio or displaying text: {e}")

if __name__ == "__main__":
    main()
