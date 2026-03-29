"""
FastAPI WebSocket 페이스스왑 서버
서버에서 실행: python server.py

의존성:
    pip install fastapi uvicorn opencv-python-headless insightface pillow
"""

import asyncio
import base64
import json
import logging

import cv2
import insightface
import numpy as np
import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from insightface.app import FaceAnalysis

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("faceswap")

app = FastAPI()

# ── 모델 초기화 ──────────────────────────────────────────────────────────────

face_app: FaceAnalysis | None = None
swapper = None

@app.on_event("startup")
def load_models():
    global face_app, swapper

    try:
        log.info("FaceAnalysis (buffalo_l) 로드 중...")
        face_app = FaceAnalysis(name="buffalo_l")
        face_app.prepare(ctx_id=0, det_size=(640, 640))   # GPU 없으면 ctx_id=-1
        log.info("FaceAnalysis 로드 완료")
    except Exception as e:
        log.error(f"[ERROR] FaceAnalysis 로드 실패: {e}", exc_info=True)
        raise

    try:
        log.info("inswapper_128 로드 중...")
        swapper = insightface.model_zoo.get_model(
            "inswapper_128.onnx",   # server.py 와 같은 디렉터리
            download=False,
            download_zip=False,
        )
        log.info("모델 로드 완료")
    except Exception as e:
        log.error(f"[ERROR] inswapper_128 로드 실패: {e}", exc_info=True)
        raise


# ── WebSocket 엔드포인트 ──────────────────────────────────────────────────────

@app.websocket("/ws/faceswap")
async def faceswap_ws(ws: WebSocket):
    await ws.accept()
    log.info("클라이언트 연결됨")

    source_face = None

    try:
        while True:
            msg = await ws.receive()

            # ── 텍스트: 소스 얼굴 설정 ──────────────────────────────────
            if "text" in msg:
                data = json.loads(msg["text"])

                if data.get("type") == "set_source":
                    img_bytes = base64.b64decode(data["image"])
                    arr = np.frombuffer(img_bytes, dtype=np.uint8)
                    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)

                    try:
                        faces = face_app.get(img)
                    except Exception as e:
                        log.error(f"[ERROR] 소스 얼굴 감지 실패: {e}", exc_info=True)
                        await ws.send_text(f'{{"type":"error","msg":"얼굴 감지 오류: {e}"}}')
                        continue

                    if faces:
                        source_face = faces[0]
                        await ws.send_text('{"type":"ready"}')
                        log.info(f"소스 얼굴 설정 완료 (감지된 얼굴 수: {len(faces)})")
                    else:
                        log.warning("[WARN] 소스 이미지에서 얼굴을 찾지 못함")
                        await ws.send_text('{"type":"error","msg":"얼굴을 찾을 수 없습니다"}')

            # ── 바이너리: 카메라 프레임 (JPEG) ───────────────────────────
            elif "bytes" in msg:
                jpeg = msg["bytes"]

                if source_face is None:
                    await ws.send_bytes(jpeg)
                    continue

                arr = np.frombuffer(jpeg, dtype=np.uint8)
                frame = cv2.imdecode(arr, cv2.IMREAD_COLOR)
                if frame is None:
                    log.warning("[WARN] 프레임 디코딩 실패 (imdecode 반환 None)")
                    continue

                try:
                    faces = face_app.get(frame)
                    if faces:
                        result = frame.copy()
                        for face in faces:
                            result = swapper.get(result, face, source_face, paste_back=True)
                    else:
                        result = frame

                    _, buf = cv2.imencode(".jpg", result, [cv2.IMWRITE_JPEG_QUALITY, 80])
                    await ws.send_bytes(buf.tobytes())
                except Exception as e:
                    log.error(f"[ERROR] 프레임 처리 실패: {e}", exc_info=True)
                    await ws.send_bytes(jpeg)  # 실패 시 원본 전송

    except WebSocketDisconnect:
        log.info("클라이언트 연결 해제")
    except Exception as e:
        log.error(f"[ERROR] WebSocket 처리 중 예외: {type(e).__name__}: {e}", exc_info=True)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8765, log_level="info")