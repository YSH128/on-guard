#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <torch/script.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "NativeLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 시간 측정을 위한 유틸리티 함수
float get_ms(std::chrono::high_resolution_clock::time_point start){
    auto end = std::chrono::high_resolution_clock::now();
    return std::chrono::duration<float, std::milli>(end - start).count();
}

class DeepfakeDetector {
public:
    torch::jit::script::Module audioModule;
    torch::jit::script::Module faceModule;
    bool isAudioLoaded = false;
    bool isFaceLoaded = false;
    float audioLoadTime = 0.0f;
    float faceLoadTime = 0.0f;

    bool loadModels(const std::string& audioPath, const std::string& facePath) {
        bool success = true;

        if (!isAudioLoaded) {
            try {
                auto start_audio = std::chrono::high_resolution_clock::now();
                audioModule = torch::jit::load(audioPath);
                audioModule.eval();
                audioLoadTime = get_ms(start_audio);
                isAudioLoaded = true;
                LOGD("Audio model loaded successfully. Time: %.2f ms", audioLoadTime);
            } catch (const c10::Error& e) {
                LOGE("Error loading audio model");
                isAudioLoaded = false;
                success = false;
            }
        }

        if (!isFaceLoaded) {
            try {
                auto start_face = std::chrono::high_resolution_clock::now();
                faceModule = torch::jit::load(facePath);
                faceModule.eval();
                faceLoadTime = get_ms(start_face);
                isFaceLoaded = true;
                LOGD("Face model loaded successfully. Time: %.2f ms", faceLoadTime);
            } catch (const c10::Error& e) {
                LOGE("Error loading face model");
                isFaceLoaded = false;
                success = false;
            }
        }

        return success;
    }

    std::vector<float> detectAudio(const float* audioData, int length) {
        if (!isAudioLoaded) return {-1.0f, 0.0f, 0.0f};

        auto start_inference = std::chrono::high_resolution_clock::now();
        auto options = torch::TensorOptions().dtype(torch::kFloat32);
        torch::Tensor input_tensor = torch::from_blob((void*)audioData, {1, length}, options);

        std::vector<torch::jit::IValue> inputs;
        inputs.push_back(input_tensor);

        at::Tensor output = audioModule.forward(inputs).toTensor();
        float score = output.item<float>();
        
        float inferenceTime = get_ms(start_inference);
        return {score, audioLoadTime, inferenceTime};
    }

    std::vector<float> detectFace(const float* imageData) {
        if (!isFaceLoaded) return {-1.0f, -1.0f, 0.0f, 0.0f};

        auto start_inference = std::chrono::high_resolution_clock::now();
        auto options = torch::TensorOptions().dtype(torch::kFloat32);
        torch::Tensor input_tensor = torch::from_blob((void*)imageData, {1, 224, 224, 3}, options);
        input_tensor = input_tensor.permute({0, 3, 1, 2});

        float mean_vals[3] = {0.485f, 0.456f, 0.406f};
        float std_vals[3]  = {0.229f, 0.224f, 0.225f};

        auto mean = torch::from_blob(mean_vals, {1, 3, 1, 1}).clone();
        auto std  = torch::from_blob(std_vals,  {1, 3, 1, 1}).clone();

        input_tensor = (input_tensor - mean) / std;

        std::vector<torch::jit::IValue> inputs;
        inputs.push_back(input_tensor);

        at::Tensor output = faceModule.forward(inputs).toTensor();
        at::Tensor prob = torch::softmax(output, 1);

        float fakeProb = prob[0][1].item<float>();
        float realProb = prob[0][0].item<float>();
        float inferenceTime = get_ms(start_inference);

        return {fakeProb, realProb, faceLoadTime, inferenceTime};
    }
};

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

// Global instance to handle models
static DeepfakeDetector detector;

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_loadModels(
        JNIEnv* env,
        jobject /* this */,
        jstring audioModelPath,
        jstring faceModelPath) {
            
    const char* aPath = env->GetStringUTFChars(audioModelPath, nullptr);
    const char* fPath = env->GetStringUTFChars(faceModelPath, nullptr);
    
    bool loadSuccess = detector.loadModels(aPath, fPath);

    env->ReleaseStringUTFChars(audioModelPath, aPath);
    env->ReleaseStringUTFChars(faceModelPath, fPath);

    float results[3] = { loadSuccess ? 1.0f : 0.0f, detector.audioLoadTime, detector.faceLoadTime };
    jfloatArray resultMap = env->NewFloatArray(3);
    env->SetFloatArrayRegion(resultMap, 0, 3, results);

    return resultMap;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeAudio(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray audioData) {
    
    jfloat* data = env->GetFloatArrayElements(audioData, nullptr);
    jsize length = env->GetArrayLength(audioData);

    std::vector<float> results = detector.detectAudio(data, length);

    env->ReleaseFloatArrayElements(audioData, data, JNI_ABORT);

    jfloatArray resultMap = env->NewFloatArray(results.size());
    env->SetFloatArrayRegion(resultMap, 0, results.size(), results.data());
    
    return resultMap;
}

/**
 * Face Deepfake Detection
 * 동영상 경로를 받아 프레임 추출 후 Face Deepfake Detection
 * 반환: [FakeProb, RealProb, LoadTime, InferTime]
 **/
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeVisual(
        JNIEnv* env,
        jobject /* this */,
        jstring videoPath) {

    const char* vPath = env->GetStringUTFChars(videoPath, nullptr);

    // 동영상에서 프레임 추출
    std::vector<float> frameData = extractFrameFromVideo(std::string(vPath));
    env->ReleaseStringUTFChars(videoPath, vPath);

    if (frameData.empty()) {
        jfloatArray resultMap = env->NewFloatArray(4);
        float results[4] = { -2.0f, -1.0f, 0.0f, 0.0f }; // 프레임 추출 실패
        env->SetFloatArrayRegion(resultMap, 0, 4, results);
        return resultMap;
    }

    std::vector<float> results = detector.detectFace(frameData.data());

    jfloatArray resultMap = env->NewFloatArray(results.size());
    env->SetFloatArrayRegion(resultMap, 0, results.size(), results.data());

    return resultMap;
}
