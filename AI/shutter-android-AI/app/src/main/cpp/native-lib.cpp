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

// 단일 프레임 → 다중 프레임 평균으로 변경
std::vector<float> extractFrameFromVideo(const std::string& videoPath, int frameCount = 10) {
    cv::VideoCapture cap(videoPath);
    if (!cap.isOpened()) return {};

    const int W = 224, H = 224, C = 3;
    std::vector<float> averaged(W * H * C, 0.0f);
    int validCount = 0;

    // 앞에서부터 순서대로 frameCount개 추출
    for (int i = 0; i < frameCount; i++) {
        cv::Mat frame;
        cap.read(frame);
        if (frame.empty()) break;

        // 224x224로 리사이즈
        cv::Mat resized;
        cv::resize(frame, resized, cv::Size(W, H));

        // BGR → RGB
        cv::Mat rgb;
        cv::cvtColor(resized, rgb, cv::COLOR_BGR2RGB);

        // float 변환 및 0~1 정규화
        cv::Mat floatMat;
        rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

        // HWC 형태의 float 배열로 변환 (224 * 224 * 3)
        float* ptr = (float*)floatMat.datastart;
        for (int j = 0; j < W * H * C; j++) {
            averaged[j] += ptr[j];
        }
        validCount++;
    }

    cap.release();

    if (validCount == 0) return {};

    for (float& v : averaged) {
        v /= (float)validCount;
    }

    return averaged;
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
 * 동영상 경로를 받아 프레임 추출 후 Face Deepfake Detection
 * 반환: [FakeProb, RealProb, LoadTime, InferTime]
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeVisual(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jstring videoPath,
        jint frameCount) {

    // 반환: [avgFake, avgReal, loadTime, inferTime, frame0Fake, frame0Real, frame1Fake, frame1Real, ...]
    int resultSize = 4 + (int)frameCount * 2;
    jfloatArray resultMap = env->NewFloatArray(resultSize);
    std::vector<float> results(resultSize, -1.0f);

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    const char* vPath = env->GetStringUTFChars(videoPath, nullptr);


    // 수정: 먼저 string으로 변환 후 VideoCapture 생성
    std::string videoPathStr(vPath);
    env->ReleaseStringUTFChars(videoPath, vPath);

    cv::VideoCapture cap(videoPathStr);

    if (!cap.isOpened()) {
        results[0] = -2.0f;
        env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
        env->ReleaseStringUTFChars(modelPath, path);
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
        env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
        return resultMap;
    }
    results[2] = get_ms(start_load);

    const int W = 224, H = 224, C = 3;
    float totalFake = 0.0f, totalReal = 0.0f;
    int validCount = 0;

    auto start_inference = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < (int)frameCount; i++) {
        cv::Mat frame;
        cap.read(frame);
        if (frame.empty()) break;

        cv::Mat resized;
        cv::resize(frame, resized, cv::Size(W, H));
        cv::Mat rgb;
        cv::cvtColor(resized, rgb, cv::COLOR_BGR2RGB);
        cv::Mat floatMat;
        rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

        std::vector<float> frameData;
        frameData.assign((float*)floatMat.datastart, (float*)floatMat.dataend);

        auto options = torch::TensorOptions().dtype(torch::kFloat32);
        torch::Tensor input_tensor = torch::from_blob(
            frameData.data(), {1, W, H, C}, options).clone();
        input_tensor = input_tensor.permute({0, 3, 1, 2});

        // ImageNet 기준 Regularization
        float mean_vals[3] = {0.485f, 0.456f, 0.406f};
        float std_vals[3]  = {0.229f, 0.224f, 0.225f};

        auto mean = torch::from_blob(mean_vals, {1, 3, 1, 1}).clone();
        auto std  = torch::from_blob(std_vals,  {1, 3, 1, 1}).clone();
        
        input_tensor = (input_tensor - mean) / std;

        std::vector<torch::jit::IValue> inputs;
        inputs.push_back(input_tensor);
        at::Tensor output = module.forward(inputs).toTensor();
        
        at::Tensor prob = torch::softmax(output, 1);

        float fake = prob[0][1].item<float>();
        float real = prob[0][0].item<float>();

        // 각 프레임 결과 저장 (인덱스 4부터)
        results[4 + i * 2]     = fake;
        results[4 + i * 2 + 1] = real;

        totalFake += fake;
        totalReal += real;
        validCount++;
    }

    cap.release();
    results[3] = get_ms(start_inference);

    if (validCount == 0) {
        results[0] = -2.0f;
    } else {
        results[0] = totalFake / validCount; // 평균 Fake
        results[1] = totalReal / validCount; // 평균 Real
    }

    env->ReleaseStringUTFChars(modelPath, path);
    env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
    
    return resultMap;
}
