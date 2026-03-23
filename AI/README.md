### 모델 파일 경로

```
AI/                    
│   ├── shutter-android-AI/                     
│   │   ├── app/                  
│   │   │   ├── src/
│   │   │   │   ├── main/
│   │   │   │   │   ├── assets/ # audoi/face_deepfake_model.pt 모델 파일 경로
│   │   │   │   │   ├── cpp/     
│   │   │   │   │   ├── java/     
│   │   │   │   │   └── ...
│   │   ├── gradle/              
│   │   └── ...
|   └── ...
└── ...
```

### 폴더 설명
- face_deepfake_model_preprocessing
    - pytorch 모델 -> torchscript 변환
    - 페이스 딥페이크 검출 모델의 output 추가 (cam, roi)
- LipNet-PyTorch
    - LipNet 저자 코드
    - 외국어 입력 모델
- Nes2Net
    - pytorch 모델 -> torchscript 변환
    - 딥보이스 추론 모델
- olkavs-avspeech
    - Lip-reading 한국어 대상 모델
    - inference 코드에서 audio = False 설정을 통해 video 입력만으로 추론 가능
    

## 참고 사항
- 현재 모델은 torchscript
- 추론 시간을 줄이기 위해 해당 모델 최적화 필요