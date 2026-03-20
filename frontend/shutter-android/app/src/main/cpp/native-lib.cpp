#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <cmath>
#include <algorithm>
#include <opencv2/opencv.hpp>
#include <android/log.h>
#include <memory>
#include "QnnModelLoader.h"

#define LOG_TAG "NativeLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

float get_ms(std::chrono::high_resolution_clock::time_point start) {
    auto end = std::chrono::high_resolution_clock::now();
    return std::chrono::duration<float, std::milli>(end - start).count();
}

// Global Qnn Loader instance
static std::unique_ptr<QnnModelLoader> g_qnnLoader = nullptr;

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

    if (!g_qnnLoader) {
        LOGE("QNN Loader not initialized.");
        results[0] = -1.0f;
        env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
        return resultMap;
    }

    results[2] = 0.0f; // QNN is pre-initialized, loadTime is 0 here
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

    // Apply normalization properly on NHWC data using OpenCV split/merge
    std::vector<cv::Mat> channels(3);
    cv::split(floatMat, channels);
    channels[0] = (channels[0] - 0.485f) / 0.229f; // R
    channels[1] = (channels[1] - 0.456f) / 0.224f; // G
    channels[2] = (channels[2] - 0.406f) / 0.225f; // B
    cv::merge(channels, floatMat);

    std::vector<float> frameData((float*)floatMat.datastart, (float*)floatMat.dataend);

    float out[2] = {0};
    bool execSuccess = g_qnnLoader->ExecuteGraph(frameData.data(), frameData.size(), out, 2);

    float fake = 0.0f, real = 0.0f;
    if (execSuccess) {
        float max_val = std::max(out[0], out[1]);
        float exp0 = std::exp(out[0] - max_val);
        float exp1 = std::exp(out[1] - max_val);
        float sum = exp0 + exp1;
        
        real = exp0 / sum;
        fake = exp1 / sum;
    } else {
        LOGE("QNN Inference Execution Failed");
    }

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

// ---------------------------------------------------------
// QNN JNI Wrappers
// ---------------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_initQnn(
        JNIEnv *env, jobject thiz, jstring cache_dir, jstring native_lib_dir) {
    
    if (!g_qnnLoader) {
        g_qnnLoader = std::make_unique<QnnModelLoader>();
    }

    const char *cacheDirStr = env->GetStringUTFChars(cache_dir, nullptr);
    const char *nativeLibDirStr = env->GetStringUTFChars(native_lib_dir, nullptr);

    bool success = g_qnnLoader->InitSystem(std::string(cacheDirStr), std::string(nativeLibDirStr));

    env->ReleaseStringUTFChars(cache_dir, cacheDirStr);
    env->ReleaseStringUTFChars(native_lib_dir, nativeLibDirStr);

    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_loadModel(
        JNIEnv *env, jobject thiz, jstring assets_path) {
    
    if (!g_qnnLoader) return JNI_FALSE;

    const char *assetsPathStr = env->GetStringUTFChars(assets_path, nullptr);
    bool success = g_qnnLoader->LoadModel(std::string(assetsPathStr));
    env->ReleaseStringUTFChars(assets_path, assetsPathStr);

    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_buildModelCache(
        JNIEnv *env, jobject thiz, jstring assets_path) {
    
    if (!g_qnnLoader) return JNI_FALSE;

    const char *assetsPathStr = env->GetStringUTFChars(assets_path, nullptr);
    bool success = g_qnnLoader->BuildModelCache(std::string(assetsPathStr));
    env->ReleaseStringUTFChars(assets_path, assetsPathStr);

    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_releaseQnn(
        JNIEnv *env, jobject thiz) {
    if (g_qnnLoader) {
        g_qnnLoader->Cleanup();
        g_qnnLoader.reset();
    }
}

// ---------------------------------------------------------
// ONNX Runtime Fallback JNI (Non-Qualcomm)
// ---------------------------------------------------------

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_preprocessFrameForOnnx(
        JNIEnv* env,
        jobject,
        jbyteArray buffer,
        jint width,
        jint height,
        jint stride) {

    jbyte* data = env->GetByteArrayElements(buffer, nullptr);
    cv::Mat frame(height, width, CV_8UC4, (void*)data, stride);

    if (frame.empty()) {
        env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
        return env->NewFloatArray(0);
    }

    const int W = 224, H = 224;
    cv::Mat resized, rgb, floatMat;
    cv::resize(frame, resized, cv::Size(W, H));
    cv::cvtColor(resized, rgb, cv::COLOR_RGBA2RGB); 
    rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

    // Apply normalization properly using OpenCV split
    std::vector<cv::Mat> channels(3);
    cv::split(floatMat, channels);
    channels[0] = (channels[0] - 0.485f) / 0.229f; // R
    channels[1] = (channels[1] - 0.456f) / 0.224f; // G
    channels[2] = (channels[2] - 0.406f) / 0.225f; // B

    // Convert to NCHW flat array [1, 3, 224, 224] for ONNX Runtime PyTorch models.
    // Since cv::split separates channels into planar mats, we directly copy them memory-aligned.
    int numPixels = W * H;
    jfloatArray result = env->NewFloatArray(3 * numPixels);
    if (!result) {
        env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
        return nullptr;
    }

    for (int i = 0; i < 3; ++i) {
        if (!channels[i].isContinuous()) {
            channels[i] = channels[i].clone();
        }
        env->SetFloatArrayRegion(result, i * numPixels, numPixels, (const jfloat*)channels[i].datastart);
    }

    env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
    return result;
}