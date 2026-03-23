import os
import tempfile
import time
import numpy as np
import torchaudio
import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import sherpa_onnx
import httpx
from dotenv import load_dotenv

load_dotenv()

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

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
        "주어진 대화(STT 결과)의 내용을 분석하여 해당하는 의도를 모두 단답형으로 출력해주세요.\n"
        "다음 옵션 중 해당하는 것을 모두 선택하세요: '계좌 이체 요구', '특정 장소 유인', '개인정보 요구', '일상 대화'\n"
        "여러 개일 경우 쉼표(,)로 구분하고, 설명이나 부가적인 문구, 마침표 없이 선택된 문자열만 정확히 출력하세요."
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

print("Loading Sherpa-ONNX recognizer...")
recognizer = create_recognizer()
print("Recognizer loaded successfully.")

@app.websocket("/ws/stt")
async def websocket_stt_endpoint(websocket: WebSocket):
    await websocket.accept()
    print("WebSocket client connected.")
    try:
        while True:
            # Receive audio data via websocket (as bytes)
            print("[\u23f3] Waiting to receive audio data from websocket...")
            audio_bytes = await websocket.receive_bytes()
            print(f"[\u2705] Received {len(audio_bytes)} bytes of audio data.")
            
            # Save the received bytes to a temporary wav file
            with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
                temp_file.write(audio_bytes)
                temp_file_path = temp_file.name
            print(f"[\u2705] Saved temporary audio file to {temp_file_path}")
            
            try:
                # Load and preprocess audio
                waveform, sample_rate = torchaudio.load(temp_file_path)
                
                # Convert stereo to mono
                if waveform.shape[0] > 1:
                    waveform = waveform.mean(dim=0, keepdim=True)
                    
                # Resample to 16kHz if needed
                if sample_rate != 16000:
                    waveform = torchaudio.functional.resample(waveform, orig_freq=sample_rate, new_freq=16000)
                
                # Extract samples as float32
                samples = waveform.squeeze(0).numpy().astype(np.float32)

                print("[\u2699\ufe0f] Starting STT model inference using Sherpa-ONNX...")
                start_time = time.time()
                
                stream = recognizer.create_stream()
                stream.accept_waveform(16000, samples)
                recognizer.decode_stream(stream)
                
                transcription = stream.result.text
                elapsed = time.time() - start_time
                
                print(f"[\u2705] Model inference completed in {elapsed:.2f} seconds.")
                print(f"[Msg] Result text (서버 전용 출력): {transcription}")
                
                print("[\u2699\ufe0f] Starting AI intent analysis using GMS API...")
                gms_start_time = time.time()
                analysis_result = analyze_text_with_gms(transcription)
                gms_elapsed = time.time() - gms_start_time
                print(f"[\u2705] GMS API inference completed in {gms_elapsed:.2f} seconds.")
                print(f"[분석 완료] GMS intent classification result: {analysis_result}")
                
                # Send ONLY the GMS result back to the client
                await websocket.send_text(analysis_result)
                print("[Sent] Sent intent classification result to client.\n")

            except Exception as e:
                print(f"Transcription error: {e}")
                await websocket.send_text(f"Error: {e}")
            finally:
                # Clean up the temporary file
                if os.path.exists(temp_file_path):
                    os.remove(temp_file_path)

    except WebSocketDisconnect:
        print("WebSocket client disconnected.")
    except Exception as e:
        print(f"WebSocket error: {e}")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
