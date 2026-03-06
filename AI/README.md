### 모델 파일 경로

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

## 참고 사항
- 현재 모델은 torchscript
- 추론 시간을 줄이기 위해 해당 모델 최적화 필요