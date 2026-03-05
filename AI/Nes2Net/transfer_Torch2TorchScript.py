import argparse
import torch
import librosa
import numpy as np
# 모델 정의 파일이 경로에 있어야 합니다.
from models.WavLM_Nes2Net_X_SeLU import WavLM_Nes2Net_SE_cat_SeLU as Model

def main(args):
    device = torch.device("cpu")
    print(f"Device set to: {device}")

    # 1. 모델 초기화
    model = Model(args, device).to(device)

    # 2. 가중치 로드
    weights_path = 'WavLM_Nes2Net_X_SeLU_e74_seed420_valid0.04245662278274772.pt'
    print(f"Loading weights from: {weights_path}")
    
    saved_state_dict = torch.load(weights_path, map_location=device)
    # state_dict 키 구조에 따라 대응 (직접 저장한 형태에 맞춰 수정 필요할 수 있음)
    if 'state_dict' in saved_state_dict:
        saved_state_dict = saved_state_dict['state_dict']
        
    model_state_dict = model.state_dict()
    filtered_state_dict = {k: v for k, v in saved_state_dict.items() if k in model_state_dict}
    model_state_dict.update(filtered_state_dict)
    model.load_state_dict(model_state_dict)
    model.eval()
    print("Model loaded and set to eval mode.")

    # 3. 실제 오디오 데이터 로드 및 전처리 (test.flac)
    audio_path = r"testdata\Sweet-Girl.flac"
    print(f"Loading audio from: {audio_path}")
    audio, _ = librosa.load(audio_path, sr=16000, mono=True)
    
    # 모델 입력 사이즈(64000)에 맞게 자르거나 패딩
    target_length = 64000
    if len(audio) > target_length:
        audio = audio[:target_length]
    else:
        audio = np.pad(audio, (0, target_length - len(audio)), mode='constant')
    
    # Tensor 변환 [1, 64000]
    input_tensor = torch.from_numpy(audio).unsqueeze(0).to(device)

    # 4. 로컬 추론 테스트 (변환 전 결과 확인)
    with torch.no_grad():
        output = model(input_tensor)
        print(f"로컬 추론 결과 (Score): {output.item()}")

    # 5. C++ 안드로이드용 TorchScript 변환 (핵심!)
    print("Tracing the model for Android C++...")
    # 반드시 trace를 사용하여 연산 그래프를 고정합니다.
    traced_model = torch.jit.trace(model, input_tensor)

    # [중요] C++ LibTorch 환경에서는 _save_for_lite_interpreter 대신 표준 save를 사용합니다.
    # 파일 확장자는 .pt로 저장합니다.
    output_path = "voice_deepfake_model.pt"
    traced_model.save(output_path)
    print(f"Android용 모델 저장 완료: {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--agg", type=str, default='SEA', choices=['SEA', 'AttM', 'WeightedSum'])
    parser.add_argument("--dilation", type=int, default=1)
    parser.add_argument("--pool_func", type=str, default='mean', choices=['mean', 'ASTP'])
    parser.add_argument("--SE_ratio", type=int, nargs='+', default=[1])
    parser.add_argument("--Nes_ratio", type=int, nargs='+', default=[8, 8])
    parser.add_argument("--AASIST_scale", type=int, default=32, choices=[24, 32, 40, 48, 56, 64, 96])
    parser.add_argument("--model_path", type=str, default='WavLM_Nes2Net_X_SeLU_e74_seed420_valid0.04245662278274772.pt')

    args = parser.parse_args()
    main(args)