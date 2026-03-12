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

float get_ms(std::chrono::high_resolution_clock::time_point start) {
    auto end = std::chrono::high_resolution_clock::now();
    return std::chrono::duration<float, std::milli>(end - start).count();
}

class DeepfakeDetector {
public:
    torch::jit::script::Module faceModule;
    bool isFaceLoaded = false;
    float faceLoadTime = 0.0f;

    bool loadModels(const std::string& facePath) {
        bool success = true;

        if (!isFaceLoaded) {
            try {
                auto start_face = std::chrono::high_resolution_clock::now();
                faceModule = torch::jit::load(facePath);
                faceModule.eval();
                faceLoadTime = get_ms(start_face);
                isFaceLoaded = true;
                LOGD("Face model loaded. Time: %.2f ms", faceLoadTime);
            } catch (const c10::Error& e) {
                LOGE("Error loading face model");
                isFaceLoaded = false;
                success = false;
            }
        }

        return success;
    }
};

static DeepfakeDetector detector;

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_loadModels(
        JNIEnv* env,
        jobject /* this */,
        jstring faceModelPath) {

    const char* fPath = env->GetStringUTFChars(faceModelPath, nullptr);

    bool loadSuccess = detector.loadModels(fPath);

    env->ReleaseStringUTFChars(faceModelPath, fPath);

    float results[2] = { loadSuccess ? 1.0f : 0.0f, detector.faceLoadTime };
    jfloatArray resultMap = env->NewFloatArray(2);
    env->SetFloatArrayRegion(resultMap, 0, 2, results);

    return resultMap;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_detectDeepfakeVisual(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray buffer,
        jint width,
        jint height,
        jint stride) {

    // Return: [avgFake, avgReal, loadTime, inferTime, frame0Fake, frame0Real, roiL, roiT, roiR, roiB]
    int resultSize = 10;
    jfloatArray resultMap = env->NewFloatArray(resultSize);
    std::vector<float> results(resultSize, -1.0f);

    if (!detector.isFaceLoaded) {
        results[0] = -1.0f;
        env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
        return resultMap;
    }

    results[2] = detector.faceLoadTime;
    auto start_inference = std::chrono::high_resolution_clock::now();

    jbyte* data = env->GetByteArrayElements(buffer, nullptr);
    
    // Create OpenCV Mat from raw RGBA buffer
    cv::Mat frame(height, width, CV_8UC4, (void*)data, stride);

    if (frame.empty()) {
        results[0] = -2.0f;
        env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
        env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
        return resultMap;
    }

    const int W = 224, H = 224, C = 3;

    cv::Mat resized;
    cv::resize(frame, resized, cv::Size(W, H));
    
    cv::Mat rgb;
    cv::cvtColor(resized, rgb, cv::COLOR_RGBA2RGB); 
    
    cv::Mat floatMat;
    rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

    std::vector<float> frameData;
    frameData.assign((float*)floatMat.datastart, (float*)floatMat.dataend);

    auto options = torch::TensorOptions().dtype(torch::kFloat32);
    torch::Tensor input_tensor = torch::from_blob(
        frameData.data(), {1, W, H, C}, options).clone();
    input_tensor = input_tensor.permute({0, 3, 1, 2});

    float mean_vals[3] = {0.485f, 0.456f, 0.406f};
    float std_vals[3]  = {0.229f, 0.224f, 0.225f};
    auto mean = torch::from_blob(mean_vals, {1, 3, 1, 1}).clone();
    auto std  = torch::from_blob(std_vals,  {1, 3, 1, 1}).clone();
    input_tensor = (input_tensor - mean) / std;

    std::vector<torch::jit::IValue> inputs;
    inputs.push_back(input_tensor);
    at::Tensor output = detector.faceModule.forward(inputs).toTensor();
    at::Tensor prob = torch::softmax(output, 1);

    float fake = prob[0][1].item<float>();
    float real = prob[0][0].item<float>();

    results[3] = get_ms(start_inference);
    results[0] = fake; // avgFake
    results[1] = real; // avgReal
    results[4] = fake; // frame0Fake
    results[5] = real; // frame0Real
    results[6] = 0.3f; // Dummy ROI left
    results[7] = 0.22f; // Dummy ROI top
    results[8] = 0.45f; // Dummy ROI right
    results[9] = 0.567f; // Dummy ROI bottom

    env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
    env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
    
    return resultMap;
}