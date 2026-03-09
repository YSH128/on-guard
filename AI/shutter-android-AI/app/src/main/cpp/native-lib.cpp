#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <torch/script.h>
#include <opencv2/opencv.hpp>

// 시간 측정을 위한 유틸리티 함수
float get_ms(std::chrono::high_resolution_clock::time_point start) {
    auto end = std::chrono::high_resolution_clock::now();
    return std::chrono::duration<float, std::milli>(end - start).count();
}

// 동영상에서 중간 프레임 1장을 추출해 224x224x3 float 배열로 반환
std::vector<float> extractFrameFromVideo(const std::string& videoPath) {
    cv::VideoCapture cap(videoPath);
    if (!cap.isOpened()) return {};

    int totalFrames = (int)cap.get(cv::CAP_PROP_FRAME_COUNT);
    int midFrame = totalFrames / 2;
    cap.set(cv::CAP_PROP_POS_FRAMES, midFrame);

    cv::Mat frame;
    cap.read(frame);
    if (frame.empty()) return {};

    // 224x224로 리사이즈
    cv::Mat resized;
    cv::resize(frame, resized, cv::Size(224, 224));

    // BGR → RGB
    cv::Mat rgb;
    cv::cvtColor(resized, rgb, cv::COLOR_BGR2RGB);

    // float 변환 및 0~1 정규화
    cv::Mat floatMat;
    rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

    // HWC 형태의 float 배열로 변환 (224 * 224 * 3)
    std::vector<float> result;
    result.assign((float*)floatMat.datastart, (float*)floatMat.dataend);

    cap.release();
    return result;
}

/**
 * Audio Deepfake Detection
 */

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeAudio(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jfloatArray audioData) {

    // 결과 저장을 위한 배열 (0: score, 1: load_time, 2: inference_time)
    jfloatArray resultMap = env->NewFloatArray(3);
    float results[3] = { -1.0f, 0.0f, 0.0f };

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    // 1. 모델 로드 시간 측정 시작
    auto start_load = std::chrono::high_resolution_clock::now();

    torch::jit::script::Module module;
    try {
        module = torch::jit::load(path); // 오디오 모델 로드
    } catch (const c10::Error& e) {
        env->ReleaseStringUTFChars(modelPath, path);
        results[0] = -1.0f; // 로드 실패
        env->SetFloatArrayRegion(resultMap, 0, 3, results);
        return resultMap;
    }

    results[1] = get_ms(start_load);

    // 2. 데이터 준비 (JNI floatArray를 C++ 배열로 변환)
    jfloat* data = env->GetFloatArrayElements(audioData, nullptr);
    jsize length = env->GetArrayLength(audioData);

    // Raw 데이터를 PyTorch Tensor로 변환 (1D Audio: {1, length})
    auto options = torch::TensorOptions().dtype(torch::kFloat32);
    torch::Tensor input_tensor = torch::from_blob(data, {1, length}, options);

    // 3. 추론 시간 측정 시작
    auto start_inference = std::chrono::high_resolution_clock::now();

    std::vector<torch::jit::IValue> inputs;
    inputs.push_back(input_tensor);

    at::Tensor output = module.forward(inputs).toTensor(); // propagation 실행
    float score = output.item<float>(); // 결과 tensor에서 단일 값 추출

    results[2] = get_ms(start_inference);
    results[0] = score;

    // 4. 메모리 해제 및 결과 반환
    env->ReleaseFloatArrayElements(audioData, data, JNI_ABORT);
    env->ReleaseStringUTFChars(modelPath, path);

    env->SetFloatArrayRegion(resultMap, 0, 3, results);
    return resultMap;
}

/**
 * Face Deepfake Detection
 */
/**
 * 동영상 경로를 받아 프레임 추출 후 Face Deepfake Detection
 * 반환: [FakeProb, RealProb, LoadTime, InferTime]
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeVisual(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jstring videoPath) {

    // 결과: 0, Fake 확률, 1: Real 확률, 2: Load 시간, 3: Inference 시간
    jfloatArray resultMap = env->NewFloatArray(4);
    float results[4] = { -1.0f, -1.0f, 0.0f, 0.0f };

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    const char* vPath = env->GetStringUTFChars(videoPath, nullptr);

    // 동영상에서 프레임 추출
    std::vector<float> frameData = extractFrameFromVideo(std::string(vPath));
    env->ReleaseStringUTFChars(videoPath, vPath);

    if (frameData.empty()) {
        env->ReleaseStringUTFChars(modelPath, path);
        results[0] = -2.0f; // 프레임 추출 실패
        env->SetFloatArrayRegion(resultMap, 0, 4, results);
        return resultMap;
    }

    // 모델 로드
    auto start_load = std::chrono::high_resolution_clock::now();
    torch::jit::script::Module module;
    try {
        module = torch::jit::load(path);
        module.eval();
    } catch (const c10::Error& e) {
        env->ReleaseStringUTFChars(modelPath, path);
        env->SetFloatArrayRegion(resultMap, 0, 4, results);
        return resultMap;
    }
    results[2] = get_ms(start_load);

    // FloatArray → Tensor 변환 (1, 224, 224, 3) → permute → (1, 3, 224, 224)
    auto options = torch::TensorOptions().dtype(torch::kFloat32);
    torch::Tensor input_tensor = torch::from_blob(
        frameData.data(), {1, 224, 224, 3}, options).clone();
    input_tensor = input_tensor.permute({0, 3, 1, 2});

    // ImageNet 기준 Regularization
    float mean_vals[3] = {0.485f, 0.456f, 0.406f};
    float std_vals[3]  = {0.229f, 0.224f, 0.225f};

    auto mean = torch::from_blob(mean_vals, {1, 3, 1, 1}).clone();
    auto std  = torch::from_blob(std_vals,  {1, 3, 1, 1}).clone();

    input_tensor = (input_tensor - mean) / std;

    // inference
    auto start_inference = std::chrono::high_resolution_clock::now();
    std::vector<torch::jit::IValue> inputs;
    inputs.push_back(input_tensor);
    at::Tensor output = module.forward(inputs).toTensor();

    at::Tensor prob = torch::softmax(output, 1);

    results[3] = get_ms(start_inference);

    results[0] = prob[0][1].item<float>(); // Fake Prob
    results[1] = prob[0][0].item<float>(); // Real Prob

    env->ReleaseStringUTFChars(modelPath, path);
    env->SetFloatArrayRegion(resultMap, 0, 4, results);

    return resultMap;
}
