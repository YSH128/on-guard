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

// ─────────────────────────────────────────────────────────────────────────────
// ROI Box 구조체 & NMS 헬퍼
// ─────────────────────────────────────────────────────────────────────────────

struct RoiBox {
    float minx, miny, maxx, maxy;
    float score;     // 세그먼트 내 정규화 CAM 평균값
    int   frameIdx;
};

static float computeIoU(const RoiBox& a, const RoiBox& b) {
    float ix1  = std::max(a.minx, b.minx);
    float iy1  = std::max(a.miny, b.miny);
    float ix2  = std::min(a.maxx, b.maxx);
    float iy2  = std::min(a.maxy, b.maxy);
    float inter = std::max(0.0f, ix2 - ix1) * std::max(0.0f, iy2 - iy1);
    float areaA = (a.maxx - a.minx) * (a.maxy - a.miny);
    float areaB = (b.maxx - b.minx) * (b.maxy - b.miny);
    float uni   = areaA + areaB - inter;
    return (uni > 1e-6f) ? inter / uni : 0.0f;
}

// Greedy NMS → score 최고인 박스 1개 반환
static RoiBox applyNMS(std::vector<RoiBox>& boxes, float iouThresh = 0.5f) {
    std::sort(boxes.begin(), boxes.end(),
              [](const RoiBox& a, const RoiBox& b) { return a.score > b.score; });
    std::vector<bool> suppressed(boxes.size(), false);
    for (int i = 0; i < (int)boxes.size(); i++) {
        if (suppressed[i]) continue;
        for (int j = i + 1; j < (int)boxes.size(); j++)
            if (!suppressed[j] && computeIoU(boxes[i], boxes[j]) > iouThresh)
                suppressed[j] = true;
    }
    for (int i = 0; i < (int)boxes.size(); i++)
        if (!suppressed[i]) return boxes[i];
    return boxes[0];
}

static RoiBox extractRoiFromCAM(const float* camData, float threshold = 0.5f) {
    RoiBox invalid = {-1, -1, -1, -1, -1.0f, -1};

    cv::Mat cam(7, 7, CV_32F, (void*)camData);

    double mn, mx;
    cv::minMaxLoc(cam, &mn, &mx);
    if (mx - mn < 1e-6) return invalid;
    cv::Mat norm = (cam - (float)mn) / (float)(mx - mn);

    cv::Mat binary;
    cv::threshold(norm, binary, threshold, 1.0f, cv::THRESH_BINARY);
    binary.convertTo(binary, CV_8U, 255);

    cv::Mat labels;
    int nLabels = cv::connectedComponents(binary, labels, 8, CV_32S);
    if (nLabels <= 1) return invalid;

    const float SCALE = 224.0f / 7.0f;
    float bestScore = -1.0f;
    RoiBox best = invalid;

    for (int lbl = 1; lbl < nLabels; lbl++) {
        float sumCam = 0.0f;
        int   cnt = 0;
        int   r0 = 7, c0 = 7, r1 = -1, c1 = -1;

        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                if (labels.at<int>(r, c) == lbl) {
                    sumCam += norm.at<float>(r, c);
                    cnt++;
                    r0 = std::min(r0, r);  c0 = std::min(c0, c);
                    r1 = std::max(r1, r);  c1 = std::max(c1, c);
                }
            }
        }
        if (cnt == 0) continue;

        float meanScore = sumCam / cnt;
        if (meanScore > bestScore) {
            bestScore = meanScore;
            best = { c0 * SCALE, r0 * SCALE,
                     (c1 + 1) * SCALE, (r1 + 1) * SCALE,
                     meanScore, -1 };
        }
    }
    return best;
}

class DeepfakeDetector {
public:
    torch::jit::script::Module faceModule;
    bool isFaceLoaded = false;
    float faceLoadTime = 0.0f;

    bool loadModels(const std::string& facePath) {
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
                return false;
            }
        }
        return true;
    }

    struct InferResult {
        float fakeProb, realProb;
        std::vector<float> cam;
        float bbox_roi[4]; // [minx, miny, maxx, maxy]
        float bbox_score;
        bool  valid = false;
    };

    InferResult infer(const float* imageData) {
        InferResult result;
        if (!isFaceLoaded) return result;

        auto options = torch::TensorOptions().dtype(torch::kFloat32);
        torch::Tensor input_tensor = torch::from_blob(
            (void*)imageData, {1, INPUT_H, INPUT_W, INPUT_C}, options).clone();
        input_tensor = input_tensor.permute({0, 3, 1, 2});

        auto mean = torch::from_blob((void*)MEAN, {1, 3, 1, 1}).clone();
        auto std  = torch::from_blob((void*)STD,  {1, 3, 1, 1}).clone();
        input_tensor = (input_tensor - mean) / std;

        std::vector<torch::jit::IValue> inputs;
        inputs.push_back(input_tensor);

        // output: tuple(logits[1,2], cam[H,W], bbox_roi[1,4], bbox_score[1])
        auto tup      = faceModule.forward(inputs).toTuple();
        at::Tensor logits = tup->elements()[0].toTensor();
        at::Tensor cam    = tup->elements()[1].toTensor();
        at::Tensor bbox_roi = tup->elements()[2].toTensor();
        at::Tensor bbox_score = tup->elements()[3].toTensor();

        at::Tensor prob  = torch::softmax(logits, 1);
        result.fakeProb  = prob[0][1].item<float>();
        result.realProb  = prob[0][0].item<float>();

        int cam_h = cam.size(0);
        int cam_w = cam.size(1);
        int cam_size = cam_h * cam_w;
        result.cam.resize(cam_size);

        auto acc = cam.accessor<float, 2>();
        for (int r = 0; r < cam_h; r++)
            for (int c = 0; c < cam_w; c++)
                result.cam[r * cam_w + c] = acc[r][c];

        result.bbox_roi[0] = bbox_roi[0][0].item<float>(); //minx
        result.bbox_roi[1] = bbox_roi[0][1].item<float>(); //miny
        result.bbox_roi[2] = bbox_roi[0][2].item<float>(); //maxx
        result.bbox_roi[3] = bbox_roi[0][3].item<float>(); //maxy
        result.bbox_score = bbox_roi[0].item<float>();

        result.valid = true;
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
    bool loadSuccess = detector.loadModels(fPath);
    env->ReleaseStringUTFChars(faceModelPath, fPath);

    float results[2] = { loadSuccess ? 1.0f : 0.0f, detector.faceLoadTime };
    jfloatArray resultMap = env->NewFloatArray(2);
    env->SetFloatArrayRegion(resultMap, 0, 2, results);

    return resultMap;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeVisual(
        JNIEnv* env,
        jobject,
        jbyteArray buffer,
        jint width, jint height, jint stride) {

    // cam size를 모르는 상태에서 일단 실패 케이스 처리용 최소 배열 준비
    auto makeErrorResult = [&](float code) -> jfloatArray {
        jfloatArray out = env->NewFloatArray(4);
        float err[4] = { code, -1.0f, -1.0f, -1.0f };
        env->SetFloatArrayRegion(out, 0, 4, err);
        return out;
    };

    if (!detector.isFaceLoaded) return makeErrorResult(-1.0f);

    jbyte* data = env->GetByteArrayElements(buffer, nullptr);
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

    // cam size 기반으로 결과 배열 크기 동적 계산
    // [fakeProb, realProb, loadTime, inferTime, cam×N, minx, miny, maxx, maxy, bbox_score]
    int cam_size   = (int)fr.cam.size();
    int RESULT_SIZE = 4 + cam_size + 5;
    std::vector<float> results(RESULT_SIZE, -1.0f);

    results[0] = fr.fakeProb;
    results[1] = fr.realProb;
    results[2] = detector.faceLoadTime;
    results[3] = 0.0f; // inferTime은 infer() 밖에서 측정 불가 → 필요시 infer()에 타이머 추가

    std::copy(fr.cam.begin(), fr.cam.end(), results.data() + 4);

    int bbox_offset    = 4 + cam_size;
    results[bbox_offset]     = fr.bbox_roi[0]; // minx
    results[bbox_offset + 1] = fr.bbox_roi[1]; // miny
    results[bbox_offset + 2] = fr.bbox_roi[2]; // maxx
    results[bbox_offset + 3] = fr.bbox_roi[3]; // maxy
    results[bbox_offset + 4] = fr.bbox_score;

    jfloatArray resultMap = env->NewFloatArray(RESULT_SIZE);
    env->SetFloatArrayRegion(resultMap, 0, RESULT_SIZE, results.data());
    return resultMap;
}