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

// 시간 측정을 위한 유틸리티 함수
float get_ms(std::chrono::high_resolution_clock::time_point start){
    auto end = std::chrono::high_resolution_clock::now();
    return std::chrono::duration<float, std::milli>(end - start).count();
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

// 영상 처리를 위한 추가 JNI (필요시 사용)
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_processFrame(JNIEnv* env, jobject, jlong matAddr) {
    cv::Mat& frame = *(cv::Mat*)matAddr;
    cv::Mat processed = preprocessFrame(frame);
    return nullptr; 
}

 extern "C" JNIEXPORT jfloatArray JNICALL
 Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeVisual(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jfloatArray imageData) {
        
        // 결과: 0, Fake 확률, 1: Real 확률, 2: Load 시간, 3: Inference 시간
        jfloatArray resultMap = env->NewFloatArray(4);
        float results[4] = {-1.0f, -1.0f, 0.0f, 0.0f};

        const char* path = env->GetStringUTFChars(modelPath, nullptr);

        // Model Load
        auto start_load = std::chrono::high_resolution_clock::now();
        torch::jit::script::Module module;
        try{
            module = torch::jit::load(path);
            module.eval();
        } catch (const c10::Error& e) {
            env->ReleaseStringUTFChars(modelPath, path);
            env->SetFloatArrayRegion(resultMap, 0, 4, results);
            return resultMap;
        }
        results[2] = get_ms(start_load);
        
        // Java 배열을 C++ tensor로 변환
        jfloat* pData = env->GetFloatArrayElements(imageData, nullptr);

        // 1D Data -> (1, 224, 224, 3) shape
        auto options = torch::TensorOptions().dtype(torch::kFloat32);
        torch::Tensor input_tensor = torch::from_blob(pData, {1, 224, 224, 3}, options);

        // Data reshape (Batch, Channel, Height, Width) -> (Channel, Height, Width)
        input_tensor = input_tensor.permute({0, 3, 1, 2});

        auto start_inference = std::chrono::high_resolution_clock::now();
        
        // ImageNet 기준 Regularization
        float mean_vals[3] = {0.485f, 0.456f, 0.406f};
        float std_vals[3]  = {0.229f, 0.224f, 0.225f};

        auto mean = torch::from_blob(mean_vals, {1, 3, 1, 1}).clone();
        auto std  = torch::from_blob(std_vals,  {1, 3, 1, 1}).clone();

        input_tensor = (input_tensor - mean) / std;
        
        // inference
        std::vector<torch::jit::IValue> inputs;
        inputs.push_back(input_tensor);
        at::Tensor output = module.forward(inputs).toTensor();
        
        at::Tensor prob = torch::softmax(output, 1);

        results[3] = get_ms(start_inference);

        results[0] = prob[0][1].item<float>(); // Fake Prob
        results[1] = prob[0][0].item<float>(); // Real Prob

        env->ReleaseFloatArrayElements(imageData, pData, JNI_ABORT);
        env->ReleaseStringUTFChars(modelPath, path);
        env->SetFloatArrayRegion(resultMap, 0, 4, results);

        return resultMap;
    }   
