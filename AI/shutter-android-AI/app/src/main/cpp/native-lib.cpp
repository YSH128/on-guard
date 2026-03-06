//#include <jni.h>
//#include <string>
//
//extern "C" JNIEXPORT jstring JNICALL
//Java_com_example_shutter_1android_1ai_MainActivity_stringFromJNI(
//        JNIEnv* env,
//        jobject /* this */) {
//    std::string hello = "Hello from C++";
//    return env->NewStringUTF(hello.c_str());
//}

#include <jni.h>
#include <string>
#include <vector>
#include <chrono> // 시간 측정용
#include <torch/script.h>
#include <opencv2/opencv.hpp>

// 전처리 함수
cv::Mat preprocessFrame(cv::Mat& src) {
    if (src.empty()) return cv::Mat();
    cv::Mat dst;
    cv::resize(src, dst, cv::Size(224, 224));
    cv::cvtColor(dst, dst, cv::COLOR_BGR2RGB);
    return dst;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfake(
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
        module = torch::jit::load(path);
    } catch (const c10::Error& e) {
        env->ReleaseStringUTFChars(modelPath, path);
        results[0] = -1.0f; // 로드 실패
        env->SetFloatArrayRegion(resultMap, 0, 3, results);
        return resultMap;
    }

    auto end_load = std::chrono::high_resolution_clock::now();
    results[1] = std::chrono::duration<float, std::milli>(end_load - start_load).count();

    // 2. 데이터 준비
    jfloat* data = env->GetFloatArrayElements(audioData, nullptr);
    jsize length = env->GetArrayLength(audioData);
    auto options = torch::TensorOptions().dtype(torch::kFloat32);
    torch::Tensor input_tensor = torch::from_blob(data, {1, length}, options);

    // 3. 추론 시간 측정 시작
    auto start_inference = std::chrono::high_resolution_clock::now();
    
    std::vector<torch::jit::IValue> inputs;
    inputs.push_back(input_tensor);
    at::Tensor output = module.forward(inputs).toTensor();
    float score = output.item<float>();

    auto end_inference = std::chrono::high_resolution_clock::now();
    results[2] = std::chrono::duration<float, std::milli>(end_inference - start_inference).count();
    results[0] = score;

    // 4. 메모리 해제 및 결과 반환
    env->ReleaseFloatArrayElements(audioData, data, JNI_ABORT);
    env->ReleaseStringUTFChars(modelPath, path);

    env->SetFloatArrayRegion(resultMap, 0, 3, results);
    return resultMap;
}

// 영상 처리를 위한 추가 JNI (필요시 사용)
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_processFrame(JNIEnv* env, jobject, jlong matAddr) {
    cv::Mat& frame = *(cv::Mat*)matAddr;
    cv::Mat processed = preprocessFrame(frame);
    return nullptr; 
}