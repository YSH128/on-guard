#include <jni.h>
#include <string>
#include <torch/script.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define TAG "DeepfakeNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// 전역 변수로 모델 유지
torch::jit::script::Module module;
bool is_model_loaded = false;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_loadModel(
        JNIEnv* env, jobject /* this */, jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    try {
        // TorchScript 모델 로드
        module = torch::jit::load(path);
        module.eval(); // 추론 모드 설정
        is_model_loaded = true;
        LOGD("모델 로드 성공: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_TRUE;
    } catch (const c10::Error& e) {
        LOGD("모델 로드 실패: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_runInference(
        JNIEnv* env, jobject /* this */) {

    if (!is_model_loaded) return env->NewStringUTF("모델이 로드되지 않았습니다.");

    try {
        // 1. 더미 데이터 생성 (OpenCV: 256x256, RGB 3채널, 모든 값 0)
        cv::Mat dummy_img = cv::Mat::zeros(256, 256, CV_8UC3);

        // 2. OpenCV Mat -> PyTorch Tensor 변환
        // (H, W, C) -> (1, C, H, W) 형태로 변환 필요
        at::Tensor tensor = torch::from_blob(dummy_img.data, {1, 256, 256, 3}, at::kByte);
        tensor = tensor.permute({0, 3, 1, 2}); // (1, 3, 256, 256)
        tensor = tensor.to(at::kFloat).div(255.0); // 정규화 (0~1)

        // 3. 추론 실행
        c10::InferenceMode guard; // 추론 시 메모리 최적화
        auto output = module.forward({tensor}).toTensor();

        // 4. 결과 확인 (출력 텐서의 크기 등을 리턴)
        std::string result = "추론 성공! 출력 크기: " + std::to_string(output.ndimension());
        return env->NewStringUTF(result.c_str());

    } catch (const std::exception& e) {
        return env->NewStringUTF(e.what());
    }
}