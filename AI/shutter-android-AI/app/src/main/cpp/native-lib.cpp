#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <mutex>                    // [FIX 5] 멀티스레드 보호용
#include <torch/script.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "NativeLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ─── Model Parameter ───────────────
static const int INPUT_H = 224;
static const int INPUT_W = 224;
static const int INPUT_C = 3;
static const float MEAN[3] = {0.485f, 0.456f, 0.406f};
static const float STD[3]  = {0.229f, 0.224f, 0.225f};
// ─────────────────────────────────────────────────

float get_ms(std::chrono::high_resolution_clock::time_point start) {
    auto end = std::chrono::high_resolution_clock::now();
    return std::chrono::duration<float, std::milli>(end - start).count();
}


class DeepfakeDetector {
public:
    torch::jit::script::Module faceModule;
    bool  isFaceLoaded  = false;
    float faceLoadTime  = 0.0f;

    // [FIX 5] 멀티스레드 동시 접근 방지
    std::mutex mtx;

    bool loadModels(const std::string& facePath) {
        std::lock_guard<std::mutex> lock(mtx);

        if (!isFaceLoaded) {
            try {
                auto start_face = std::chrono::high_resolution_clock::now();
                faceModule = torch::jit::load(facePath);
                faceModule.eval();
                faceLoadTime = get_ms(start_face);
                isFaceLoaded = true;
                LOGD("Face model loaded. Time: %.2f ms", faceLoadTime);
            } catch (const c10::Error& e) {
                LOGE("Error loading face model: %s", e.what());
                isFaceLoaded = false;
                return false;
            }
        }
        return true;
    }

    struct InferResult {
        float fakeProb   = 0.f, realProb = 0.f;
        std::vector<float> cam;
        float bbox_roi[4] = {-1.f, -1.f, -1.f, -1.f};
        float bbox_score  = -1.f;
        float inferTimeMs = 0.f;    // [FIX 1] infer() 내부에서 직접 측정
        bool  valid       = false;
    };

    InferResult infer(const float* imageData) {
        InferResult result;

        std::lock_guard<std::mutex> lock(mtx);   // [FIX 5]
        if (!isFaceLoaded) return result;

        // [FIX 1] infer() 안에서 추론 시간 측정 시작
        auto start_infer = std::chrono::high_resolution_clock::now();

        try {
            auto options = torch::TensorOptions().dtype(torch::kFloat32);
            torch::Tensor input_tensor = torch::from_blob(
                    (void*)imageData, {1, INPUT_H, INPUT_W, INPUT_C}, options).clone();
            input_tensor = input_tensor.permute({0, 3, 1, 2});

            // [FIX 6] const float[] → 임시 배열로 복사해 const 제거 없이 from_blob 사용
            float mean_buf[3] = {MEAN[0], MEAN[1], MEAN[2]};
            float std_buf[3]  = {STD[0],  STD[1],  STD[2]};
            auto mean_t = torch::from_blob(mean_buf, {1, 3, 1, 1}).clone();
            auto std_t  = torch::from_blob(std_buf,  {1, 3, 1, 1}).clone();
            input_tensor = (input_tensor - mean_t) / std_t;

            std::vector<torch::jit::IValue> inputs;
            inputs.push_back(input_tensor);

            auto output = faceModule.forward(inputs);
            if (!output.isTuple()) { LOGE("Output is not a tuple"); return result; }

            auto   tup = output.toTuple();
            size_t n   = tup->elements().size();
            LOGD("tuple size: %zu", n);
            if (n < 4) { LOGE("Expected 4-element tuple, got %zu", n); return result; }

            // ── [0] logits ──────────────────────────────────────────────────
            if (!tup->elements()[0].isTensor()) { LOGE("elements[0] not Tensor"); return result; }
            at::Tensor logits = tup->elements()[0].toTensor();
            at::Tensor prob   = torch::softmax(logits, 1);
            result.fakeProb   = prob[0][1].item<float>();
            result.realProb   = prob[0][0].item<float>();
            LOGD("fakeProb=%.4f realProb=%.4f", result.fakeProb, result.realProb);

            // ── [1] cam [H, W] ───────────────────────────────────────────────
            if (!tup->elements()[1].isTensor()) { LOGE("elements[1] not Tensor"); return result; }
            at::Tensor cam = tup->elements()[1].toTensor();
            while (cam.dim() > 2 && cam.size(0) == 1) cam = cam.squeeze(0);

            // [FIX 3] CAM 차원 검증
            if (cam.dim() != 2) {
                LOGE("Unexpected CAM dim: %lld (expected 2)", (long long)cam.dim());
                return result;
            }
            int cam_h = (int)cam.size(0);
            int cam_w = (int)cam.size(1);
            LOGD("CAM: %d x %d", cam_h, cam_w);
            result.cam.resize(cam_h * cam_w);
            auto acc = cam.accessor<float, 2>();
            for (int r = 0; r < cam_h; r++)
                for (int c = 0; c < cam_w; c++)
                    result.cam[r * cam_w + c] = acc[r][c];

            // ── [2] bbox_roi ─────────────────────────────────────────────────
            if (!tup->elements()[2].isTensor()) { LOGE("elements[2] not Tensor"); return result; }
            at::Tensor bbox_roi = tup->elements()[2].toTensor();
            while (bbox_roi.dim() > 1 && bbox_roi.size(0) == 1) bbox_roi = bbox_roi.squeeze(0);

            // [FIX 4] bbox 크기 검증 (최소 4개 원소 필요)
            if (bbox_roi.dim() != 1 || bbox_roi.size(0) < 4) {
                LOGE("Unexpected bbox_roi shape: dim=%lld size=%lld",
                     (long long)bbox_roi.dim(),
                     bbox_roi.dim() > 0 ? (long long)bbox_roi.size(0) : -1LL);
                return result;
            }
            result.bbox_roi[0] = bbox_roi[0].item<float>(); // minx
            result.bbox_roi[1] = bbox_roi[1].item<float>(); // miny
            result.bbox_roi[2] = bbox_roi[2].item<float>(); // maxx
            result.bbox_roi[3] = bbox_roi[3].item<float>(); // maxy
            LOGD("bbox: %.1f %.1f %.1f %.1f",
                 result.bbox_roi[0], result.bbox_roi[1],
                 result.bbox_roi[2], result.bbox_roi[3]);

            // ── [3] bbox_score ───────────────────────────────────────────────
            if (!tup->elements()[3].isTensor()) { LOGE("elements[3] not Tensor"); return result; }
            at::Tensor bbox_score_t = tup->elements()[3].toTensor();
            result.bbox_score = bbox_score_t[0].item<float>();
            LOGD("bbox_score: %.4f", result.bbox_score);

            // [FIX 1] 추론 시간 저장
            result.inferTimeMs = get_ms(start_infer);
            result.valid = true;

        } catch (const c10::Error& e) {
            LOGE("c10::Error in infer: %s", e.what());
        } catch (const std::exception& e) {
            LOGE("std::exception in infer: %s", e.what());
        }
        return result;
    }
};

static DeepfakeDetector detector;

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_loadModels(
        JNIEnv* env,
        jobject /* this */,
        jstring faceModelPath) {

    const char* fPath = env->GetStringUTFChars(faceModelPath, nullptr);
    bool loadSuccess  = detector.loadModels(fPath);
    env->ReleaseStringUTFChars(faceModelPath, fPath);

    float results[2] = { loadSuccess ? 1.0f : 0.0f, detector.faceLoadTime };
    jfloatArray resultMap = env->NewFloatArray(2);
    env->SetFloatArrayRegion(resultMap, 0, 2, results);
    return resultMap;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeVisual(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray buffer,
        jint width,
        jint height,
        jint stride) {

    auto makeErrorResult = [&](float code) -> jfloatArray {
        jfloatArray out = env->NewFloatArray(4);
        float err[4] = { code, -1.0f, -1.0f, -1.0f };
        env->SetFloatArrayRegion(out, 0, 4, err);
        return out;
    };

    // [FIX 7] isFaceLoaded 확인은 GetByteArrayElements 이전에 수행 → data 누수 없음 (기존 구조 유지)
    if (!detector.isFaceLoaded) return makeErrorResult(-1.0f);

    jbyte* data = env->GetByteArrayElements(buffer, nullptr);
    if (!data) return makeErrorResult(-2.0f);   // null 체크 추가

    cv::Mat frame(height, width, CV_8UC4, (void*)data, stride);

    if (frame.empty()) {
        env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
        return makeErrorResult(-2.0f);
    }

    cv::Mat resized, rgb, floatMat;
    cv::resize(frame, resized, cv::Size(INPUT_W, INPUT_H));
    cv::cvtColor(resized, rgb, cv::COLOR_RGBA2RGB);
    rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

    std::vector<float> frameData(
            (float*)floatMat.datastart, (float*)floatMat.dataend);

    auto fr = detector.infer(frameData.data());

    env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);

    if (!fr.valid) return makeErrorResult(-3.0f);

    // [fakeProb, realProb, loadTime, inferTime, cam×N, minx, miny, maxx, maxy, bbox_score]
    int cam_size    = (int)fr.cam.size();
    int RESULT_SIZE = 4 + cam_size + 5;
    std::vector<float> results(RESULT_SIZE, -1.0f);

    results[0] = fr.fakeProb;
    results[1] = fr.realProb;
    results[2] = detector.faceLoadTime;
    results[3] = fr.inferTimeMs;    // [FIX 1] infer() 내부에서 측정한 정확한 추론 시간

    std::copy(fr.cam.begin(), fr.cam.end(), results.data() + 4);

    int bbox_offset          = 4 + cam_size;
    results[bbox_offset]     = fr.bbox_roi[0]; // minx
    results[bbox_offset + 1] = fr.bbox_roi[1]; // miny
    results[bbox_offset + 2] = fr.bbox_roi[2]; // maxx
    results[bbox_offset + 3] = fr.bbox_roi[3]; // maxy
    results[bbox_offset + 4] = fr.bbox_score;

    jfloatArray resultMap = env->NewFloatArray(RESULT_SIZE);
    env->SetFloatArrayRegion(resultMap, 0, RESULT_SIZE, results.data());
    return resultMap;
}
