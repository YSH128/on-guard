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
#include "onnxruntime_c_api.h"
// 💡 NNAPI 가속을 위한 헤더 추가
#include "nnapi_provider_factory.h"

#define LOG_TAG "NativeLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ─── Float16 변환 헬퍼 ────────────────────────────────────
static uint16_t f32_to_f16(float f) {
    uint32_t x;
    memcpy(&x, &f, sizeof(x));
    uint16_t sign = (uint16_t)((x >> 31) << 15);
    int      exp  = (int)((x >> 23) & 0xFF) - 127 + 15;
    uint32_t mant = x & 0x7FFFFF;
    if (exp <= 0) return sign;
    if (exp >= 31) return sign | 0x7C00;
    return sign | (uint16_t)(exp << 10) | (uint16_t)(mant >> 13);
}

static float f16_to_f32(uint16_t h) {
    uint32_t sign = (uint32_t)(h >> 15) << 31;
    uint32_t exp  = (uint32_t)((h >> 10) & 0x1F);
    uint32_t mant = (uint32_t)(h & 0x3FF);
    uint32_t v;
    if (exp == 0)       { v = sign; }
    else if (exp == 31) { v = sign | 0x7F800000 | mant; }
    else                { v = sign | ((exp - 15 + 127) << 23) | (mant << 13); }
    float r; memcpy(&r, &v, sizeof(r)); return r;
}
// ──────────────────────────────────────────────────────────

// ─── Model Parameters ─────────────────────────────────────
static const int   INPUT_H        = 256;
static const int   INPUT_W        = 256;
static const float ROI_THRESHOLD  = 0.6f;
// true  = 모델 입출력이 float16 (양자화 모델)
// false = 모델 입출력이 float32 (원본 모델)
static const bool  USE_FLOAT16_IO = true;
// ──────────────────────────────────────────────────────────

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

    int resultSize = 9;
    jfloatArray resultMap = env->NewFloatArray(resultSize);
    std::vector<float> results(resultSize, -1.0f);

    if (!g_qnnLoader) {
        LOGE("QNN Loader not initialized.");
        results[0] = -1.0f;
        env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
        return resultMap;
    }

    results[2] = 0.0f;
    auto start_inference = std::chrono::high_resolution_clock::now();
    jbyte* data = env->GetByteArrayElements(buffer, nullptr);
    cv::Mat frame(height, width, CV_8UC4, (void*)data, stride);

    if (frame.empty()) {
        results[0] = -2.0f;
        env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
        env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
        return resultMap;
    }

    cv::Mat resized;
    cv::resize(frame, resized, cv::Size(INPUT_W, INPUT_H));
    cv::Mat rgb;
    cv::cvtColor(resized, rgb, cv::COLOR_RGBA2RGB);
    cv::Mat floatMat;
    rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

    std::vector<cv::Mat> channels(3);
    cv::split(floatMat, channels);
    channels[0] = (channels[0] - 0.485f) / 0.229f;
    channels[1] = (channels[1] - 0.456f) / 0.224f;
    channels[2] = (channels[2] - 0.406f) / 0.225f;
    cv::merge(channels, floatMat);

    std::vector<float> frameData((float*)floatMat.datastart, (float*)floatMat.dataend);

    int cam_h = (int)g_qnnLoader->m_camH;
    int cam_w = (int)g_qnnLoader->m_camW;
    float logitsOut[2] = {0.f, 0.f};
    std::vector<float> camOut(cam_h * cam_w, 0.f);

    bool execSuccess = g_qnnLoader->ExecuteGraph(
            frameData.data(), frameData.size(),
            logitsOut, 2,
            camOut.data(), (size_t)(cam_h * cam_w)
    );

    if (!execSuccess) {
        LOGE("QNN Inference Execution Failed");
        env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
        env->SetFloatArrayRegion(resultMap, 0, resultSize, results.data());
        return resultMap;
    }

    float max_val = std::max(logitsOut[0], logitsOut[1]);
    float exp0    = std::exp(logitsOut[0] - max_val);
    float exp1    = std::exp(logitsOut[1] - max_val);
    float sum     = exp0 + exp1;
    results[0] = exp1 / sum;
    results[1] = exp0 / sum;
    results[3] = get_ms(start_inference);

    if (cam_h > 0 && cam_w > 0) {
        cv::Mat cam_mat(cam_h, cam_w, CV_32F, camOut.data());

        // sigmoid 출력이지만 절대값이 작아 직접 threshold 불가.
        // min-max 정규화로 상대적 활성화 패턴 기반 ROI 추출 (ONNX path와 동일).
        double minVal, maxVal;
        cv::minMaxLoc(cam_mat, &minVal, &maxVal);
        LOGD("[CAM] min=%.4f max=%.4f range=%.4f", (float)minVal, (float)maxVal, (float)(maxVal - minVal));

        if (maxVal - minVal > 1e-6) {
            cv::Mat cam_norm = (cam_mat - minVal) / (maxVal - minVal);
            cv::Mat cam_upsampled;
            cv::resize(cam_norm, cam_upsampled, cv::Size(INPUT_W, INPUT_H), 0, 0, cv::INTER_LINEAR);

            cv::Mat binary;
            cv::threshold(cam_upsampled, binary, ROI_THRESHOLD, 1.0, cv::THRESH_BINARY);
            binary.convertTo(binary, CV_8U);

            cv::Mat labels, stats, centroids;
            int num_labels = cv::connectedComponentsWithStats(binary, labels, stats, centroids);
            LOGD("[CAM] components=%d", num_labels - 1);

            float norm = 1.0f / (float)INPUT_W;
            float best_score = -1.f;

            for (int i = 1; i < num_labels; i++) {
                int left   = stats.at<int>(i, cv::CC_STAT_LEFT);
                int top    = stats.at<int>(i, cv::CC_STAT_TOP);
                int w      = stats.at<int>(i, cv::CC_STAT_WIDTH);
                int h      = stats.at<int>(i, cv::CC_STAT_HEIGHT);
                int area   = stats.at<int>(i, cv::CC_STAT_AREA);

                float score_sum = 0.f;
                for (int r = top; r < top + h; r++)
                    for (int c = left; c < left + w; c++)
                        if (labels.at<int>(r, c) == i)
                            score_sum += cam_upsampled.at<float>(r, c);

                float mean_score = score_sum / area;
                if (mean_score > best_score) {
                    best_score = mean_score;
                    results[4] = (float)left       * norm;
                    results[5] = (float)top        * norm;
                    results[6] = (float)(left + w) * norm;
                    results[7] = (float)(top  + h) * norm;
                    results[8] = mean_score;
                }
            }
            LOGD("[CAM] ROI=[%.3f,%.3f,%.3f,%.3f] score=%.4f",
                 results[4], results[5], results[6], results[7], best_score);
        }
    }

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
    if (!g_qnnLoader) g_qnnLoader = std::make_unique<QnnModelLoader>();
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
    if (g_qnnLoader) { g_qnnLoader->Cleanup(); g_qnnLoader.reset(); }
}

// ---------------------------------------------------------
// ONNX Runtime C API (GPU/NNAPI 가속 추가)
// ---------------------------------------------------------

static const OrtApi* g_ort         = nullptr;
static OrtEnv* g_ort_env     = nullptr;
static OrtSession* g_ort_session = nullptr;
static int              g_ort_cam_h   = 0;
static int              g_ort_cam_w   = 0;

#define ORT_CHECK(expr) \
    do { \
        OrtStatus* _s = (expr); \
        if (_s) { \
            LOGE("ORT Error: %s", g_ort->GetErrorMessage(_s)); \
            g_ort->ReleaseStatus(_s); \
            return false; \
        } \
    } while(0)

static bool ort_init_session(const char* model_path) {
    g_ort = OrtGetApiBase()->GetApi(ORT_API_VERSION);
    if (!g_ort) { LOGE("OrtGetApiBase failed"); return false; }
    ORT_CHECK(g_ort->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "deepfake", &g_ort_env));

    OrtSessionOptions* opts = nullptr;
    ORT_CHECK(g_ort->CreateSessionOptions(&opts));

    // NNAPI GPU/NPU 가속 (GPU 미사용 시 CPU 자동 폴백)
    // TODO: 영상통화 중 GPU 충돌 재발 시 아래 블록 주석 처리 후 CPU 전용 모드로 전환
    uint32_t nnapi_flags = 0;
    OrtStatus* nnapi_status = OrtSessionOptionsAppendExecutionProvider_Nnapi(opts, nnapi_flags);
    if (nnapi_status) {
        LOGW("NNAPI not available, falling back to CPU. Error: %s", g_ort->GetErrorMessage(nnapi_status));
        g_ort->ReleaseStatus(nnapi_status);
    } else {
        LOGD("NNAPI GPU/NPU acceleration enabled!");
    }

    g_ort->SetIntraOpNumThreads(opts, 2);
    ORT_CHECK(g_ort->CreateSession(g_ort_env, model_path, opts, &g_ort_session));
    g_ort->ReleaseSessionOptions(opts);

    // CAM 출력 크기 자동 검출 (warmup 추론)
    {
        OrtMemoryInfo* mem = nullptr;
        g_ort->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &mem);
        int64_t shape[] = {1, 3, INPUT_H, INPUT_W};
        size_t elem = (size_t)(INPUT_H * INPUT_W * 3);
        OrtValue* in_t = nullptr;
        if (USE_FLOAT16_IO) {
            std::vector<uint16_t> dummy(elem, f32_to_f16(0.0f));
            g_ort->CreateTensorWithDataAsOrtValue(mem, dummy.data(), elem * sizeof(uint16_t),
                shape, 4, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16, &in_t);
        } else {
            std::vector<float> dummy(elem, 0.0f);
            g_ort->CreateTensorWithDataAsOrtValue(mem, dummy.data(), elem * sizeof(float),
                shape, 4, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &in_t);
        }
        g_ort->ReleaseMemoryInfo(mem);
        const char* in_n[]  = {"input"};
        const char* out_n[] = {"logits", "cam"};
        OrtValue* out_t[2]  = {nullptr, nullptr};
        OrtStatus* st = g_ort->Run(g_ort_session, nullptr, in_n, (const OrtValue* const*)&in_t, 1, out_n, 2, out_t);
        g_ort->ReleaseValue(in_t);
        if (st) {
            LOGE("Warmup failed: %s", g_ort->GetErrorMessage(st));
            g_ort->ReleaseStatus(st);
            return false;
        }
        OrtTensorTypeAndShapeInfo* info = nullptr;
        g_ort->GetTensorTypeAndShape(out_t[1], &info);
        size_t ndim = 0;
        g_ort->GetDimensionsCount(info, &ndim);
        std::vector<int64_t> dims(ndim);
        g_ort->GetDimensions(info, dims.data(), ndim);
        g_ort->ReleaseTensorTypeAndShapeInfo(info);
        g_ort_cam_h = (ndim >= 3) ? (int)dims[ndim - 2] : 1;
        g_ort_cam_w = (ndim >= 4) ? (int)dims[ndim - 1] : 1;
        g_ort->ReleaseValue(out_t[0]);
        g_ort->ReleaseValue(out_t[1]);
    }
    LOGD("ONNX session init OK. cam: %d x %d (auto-detected)", g_ort_cam_h, g_ort_cam_w);
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_initOnnx(
        JNIEnv* env, jobject, jstring model_path_j) {
    if (g_ort_session) return JNI_TRUE;
    const char* path = env->GetStringUTFChars(model_path_j, nullptr);
    bool ok = ort_init_session(path);
    env->ReleaseStringUTFChars(model_path_j, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_releaseOnnx(
        JNIEnv*, jobject) {
    if (g_ort_session) { g_ort->ReleaseSession(g_ort_session); g_ort_session = nullptr; }
    if (g_ort_env)     { g_ort->ReleaseEnv(g_ort_env);         g_ort_env     = nullptr; }
    g_ort_cam_h = g_ort_cam_w = 0;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_s14p21a404_shutter_data_detector_bridge_NativeDeepfakeBridge_detectDeepfakeOnnx(
        JNIEnv* env, jobject,
        jbyteArray buffer, jint width, jint height, jint stride) {

    const int resultSize = 9;
    jfloatArray resultArr = env->NewFloatArray(resultSize);
    std::vector<float> results(resultSize, -1.f);

    if (!g_ort_session) {
        LOGE("ONNX session not initialized");
        env->SetFloatArrayRegion(resultArr, 0, resultSize, results.data());
        return resultArr;
    }

    auto t0 = std::chrono::high_resolution_clock::now();
    jbyte* data = env->GetByteArrayElements(buffer, nullptr);
    cv::Mat frame(height, width, CV_8UC4, (void*)data, stride);
    if (frame.empty()) {
        results[0] = -2.f;
        env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
        env->SetFloatArrayRegion(resultArr, 0, resultSize, results.data());
        return resultArr;
    }

    cv::Mat resized, rgb, floatMat;
    cv::resize(frame, resized, cv::Size(INPUT_W, INPUT_H));
    cv::cvtColor(resized, rgb, cv::COLOR_RGBA2RGB);
    rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

    std::vector<cv::Mat> ch(3);
    cv::split(floatMat, ch);
    ch[0] = (ch[0] - 0.485f) / 0.229f;
    ch[1] = (ch[1] - 0.456f) / 0.224f;
    ch[2] = (ch[2] - 0.406f) / 0.225f;

    int numPx = INPUT_W * INPUT_H;
    env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);

    OrtMemoryInfo* mem_info = nullptr;
    g_ort->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &mem_info);
    int64_t in_shape[] = {1, 3, INPUT_H, INPUT_W};
    OrtValue* in_tensor = nullptr;
    if (USE_FLOAT16_IO) {
        std::vector<uint16_t> inputData(3 * numPx);
        for (int i = 0; i < 3; ++i) {
            if (!ch[i].isContinuous()) ch[i] = ch[i].clone();
            const float* src = (const float*)ch[i].datastart;
            for (int j = 0; j < numPx; ++j)
                inputData[i * numPx + j] = f32_to_f16(src[j]);
        }
        g_ort->CreateTensorWithDataAsOrtValue(mem_info, inputData.data(), inputData.size() * sizeof(uint16_t), in_shape, 4, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16, &in_tensor);
    } else {
        std::vector<float> inputData(3 * numPx);
        for (int i = 0; i < 3; ++i) {
            if (!ch[i].isContinuous()) ch[i] = ch[i].clone();
            const float* src = (const float*)ch[i].datastart;
            for (int j = 0; j < numPx; ++j)
                inputData[i * numPx + j] = src[j];
        }
        g_ort->CreateTensorWithDataAsOrtValue(mem_info, inputData.data(), inputData.size() * sizeof(float), in_shape, 4, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &in_tensor);
    }
    g_ort->ReleaseMemoryInfo(mem_info);

    const char* in_names[]  = {"input"};
    const char* out_names[] = {"logits", "cam"};
    OrtValue* out_vals[2] = {nullptr, nullptr};
    OrtStatus* run_status = g_ort->Run(g_ort_session, nullptr, in_names,  (const OrtValue* const*)&in_tensor, 1, out_names, 2, out_vals);
    g_ort->ReleaseValue(in_tensor);

    if (run_status) {
        LOGE("ONNX Run failed: %s", g_ort->GetErrorMessage(run_status));
        g_ort->ReleaseStatus(run_status);
        env->SetFloatArrayRegion(resultArr, 0, resultSize, results.data());
        return resultArr;
    }

    results[2] = 0.f;
    results[3] = get_ms(t0);

    // logits 읽기 (USE_FLOAT16_IO 분기)
    float logit0, logit1;
    if (USE_FLOAT16_IO) {
        uint16_t* logits_fp16 = nullptr;
        g_ort->GetTensorMutableData(out_vals[0], (void**)&logits_fp16);
        logit0 = f16_to_f32(logits_fp16[0]);
        logit1 = f16_to_f32(logits_fp16[1]);
    } else {
        float* logits_fp32 = nullptr;
        g_ort->GetTensorMutableData(out_vals[0], (void**)&logits_fp32);
        logit0 = logits_fp32[0];
        logit1 = logits_fp32[1];
    }
    float max_v = std::max(logit0, logit1);
    float e0 = std::exp(logit0 - max_v);
    float e1 = std::exp(logit1 - max_v);
    float s  = e0 + e1;
    results[0] = e1 / s;
    results[1] = e0 / s;
    g_ort->ReleaseValue(out_vals[0]);

    // cam 읽기 (USE_FLOAT16_IO 분기) 후 OpenCV 처리
    int cam_h = g_ort_cam_h, cam_w = g_ort_cam_w;
    if (cam_h > 0 && cam_w > 0) {
        std::vector<float> cam_f32(cam_h * cam_w);
        if (USE_FLOAT16_IO) {
            uint16_t* cam_fp16 = nullptr;
            g_ort->GetTensorMutableData(out_vals[1], (void**)&cam_fp16);
            for (int i = 0; i < cam_h * cam_w; ++i)
                cam_f32[i] = f16_to_f32(cam_fp16[i]);
        } else {
            float* cam_fp32 = nullptr;
            g_ort->GetTensorMutableData(out_vals[1], (void**)&cam_fp32);
            std::copy(cam_fp32, cam_fp32 + cam_h * cam_w, cam_f32.begin());
        }
        cv::Mat cam_mat(cam_h, cam_w, CV_32F, cam_f32.data());

        // sigmoid 출력이지만 절대값이 0.02~0.03 수준으로 작아 직접 threshold 불가.
        // min-max 정규화로 상대적 활성화 패턴 기반 ROI 추출.
        double minVal, maxVal;
        cv::minMaxLoc(cam_mat, &minVal, &maxVal);

        LOGD("[CAM] min=%.4f max=%.4f range=%.4f", (float)minVal, (float)maxVal, (float)(maxVal - minVal));

        if (maxVal - minVal > 1e-6) {
            cv::Mat cam_norm = (cam_mat - minVal) / (maxVal - minVal);
            cv::Mat cam_upsampled;
            cv::resize(cam_norm, cam_upsampled, cv::Size(INPUT_W, INPUT_H), 0, 0, cv::INTER_LINEAR);

            cv::Mat binary;
            cv::threshold(cam_upsampled, binary, ROI_THRESHOLD, 1.0, cv::THRESH_BINARY);
            binary.convertTo(binary, CV_8U);

            cv::Mat labels, stats, centroids;
            int num_labels = cv::connectedComponentsWithStats(binary, labels, stats, centroids);
            LOGD("[CAM] threshold=%.1f components=%d", ROI_THRESHOLD, num_labels - 1);

            float norm  = 1.0f / (float)INPUT_W;
            float best_score = -1.f;

            for (int i = 1; i < num_labels; i++) {
                int left = stats.at<int>(i, cv::CC_STAT_LEFT);
                int top  = stats.at<int>(i, cv::CC_STAT_TOP);
                int w    = stats.at<int>(i, cv::CC_STAT_WIDTH);
                int h    = stats.at<int>(i, cv::CC_STAT_HEIGHT);
                int area = stats.at<int>(i, cv::CC_STAT_AREA);

                float score_sum = 0.f;
                for (int r = top; r < top + h; r++)
                    for (int c = left; c < left + w; c++)
                        if (labels.at<int>(r, c) == i)
                            score_sum += cam_upsampled.at<float>(r, c);

                float mean_score = score_sum / area;
                if (mean_score > best_score) {
                    best_score    = mean_score;
                    results[4]    = (float)left        * norm;
                    results[5]    = (float)top         * norm;
                    results[6]    = (float)(left + w)  * norm;
                    results[7]    = (float)(top  + h)  * norm;
                    results[8]    = mean_score;
                }
            }
            LOGD("[CAM] ROI=[%.3f,%.3f,%.3f,%.3f] score=%.4f",
                 results[4], results[5], results[6], results[7], best_score);
        }
    }
    g_ort->ReleaseValue(out_vals[1]);

    env->SetFloatArrayRegion(resultArr, 0, resultSize, results.data());
    return resultArr;
}