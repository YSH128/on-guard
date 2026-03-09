#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
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

    // 새 모델 output: tuple(logits[1,2], cam[7,7])
    struct InferResult {
        float fakeProb, realProb;
        float cam[49];   // 7×7, row-major
        bool  valid = false;
    };

    InferResult inferWithCAM(const float* imageData) {
        InferResult result;
        if (!isFaceLoaded) return result;

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

        // 모델 output이 tuple(logits[1,2], cam[7,7])로 변경됨
        auto tup      = faceModule.forward(inputs).toTuple();
        at::Tensor logits = tup->elements()[0].toTensor();
        at::Tensor cam    = tup->elements()[1].toTensor();

        at::Tensor prob  = torch::softmax(logits, 1);
        result.fakeProb  = prob[0][1].item<float>();
        result.realProb  = prob[0][0].item<float>();

        auto acc = cam.accessor<float, 2>();
        for (int r = 0; r < 7; r++)
            for (int c = 0; c < 7; c++)
                result.cam[r * 7 + c] = acc[r][c];
        result.valid = true;
        return result;
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

// 동영상에서 numFrames개 균등 간격 프레임을 추출해 각각 224x224x3 float 배열로 반환
std::vector<std::vector<float>> extractFramesFromVideo(const std::string& videoPath, int numFrames = 10) {
    std::vector<std::vector<float>> frames;
    cv::VideoCapture cap(videoPath);
    if (!cap.isOpened()) return frames;

    int totalFrames = (int)cap.get(cv::CAP_PROP_FRAME_COUNT);
    if (totalFrames <= 0) { cap.release(); return frames; }

    for (int i = 0; i < numFrames; i++) {
        int frameIdx = (int)((float)i / numFrames * totalFrames);
        cap.set(cv::CAP_PROP_POS_FRAMES, frameIdx);

        cv::Mat frame;
        cap.read(frame);
        if (frame.empty()) continue;

        cv::Mat resized;
        cv::resize(frame, resized, cv::Size(224, 224));

        cv::Mat rgb;
        cv::cvtColor(resized, rgb, cv::COLOR_BGR2RGB);

        cv::Mat floatMat;
        rgb.convertTo(floatMat, CV_32F, 1.0f / 255.0f);

        std::vector<float> frameData;
        frameData.assign((float*)floatMat.datastart, (float*)floatMat.dataend);
        frames.push_back(frameData);
    }

    cap.release();
    return frames;
}

// CAM[7×7] → 연결 세그먼트 중 평균 score 최대인 박스 반환 (224×224 픽셀 기준)
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
 * 동영상 경로를 받아 10프레임 추출 후 Face Deepfake Detection + CAM ROI 추출
 * 반환: [avgFakeProb, avgRealProb, LoadTime, InferTime,
 *        minx, miny, maxx, maxy, bestFrameIdx, cam[49]]  총 58개
 *   minx~maxy: 224×224 픽셀 기준 (-1이면 ROI 없음)
 *   cam[49]: 7×7 CAM row-major (시각화용)
 **/
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_shutter_1android_1ai_MainActivity_detectDeepfakeVisual(
        JNIEnv* env,
        jobject /* this */,
        jstring videoPath) {

    const char* vPath = env->GetStringUTFChars(videoPath, nullptr);
    std::string vPathStr(vPath);
    env->ReleaseStringUTFChars(videoPath, vPath);

    auto inferStart = std::chrono::high_resolution_clock::now();

    // 10프레임 균등 추출
    auto frames = extractFramesFromVideo(vPathStr, 10);
    if (frames.empty()) {
        float results[9] = { -2.0f, -1.0f, 0.0f, 0.0f, -1, -1, -1, -1, -1 };
        jfloatArray resultMap = env->NewFloatArray(9);
        env->SetFloatArrayRegion(resultMap, 0, 9, results);
        return resultMap;
    }

    float totalFake = 0.0f, totalReal = 0.0f;
    int   validCount = 0;
    std::vector<RoiBox> boxes;

    for (int i = 0; i < (int)frames.size(); i++) {
        auto fr = detector.inferWithCAM(frames[i].data());
        if (!fr.valid) continue;

        totalFake += fr.fakeProb;
        totalReal += fr.realProb;
        validCount++;

        RoiBox box = extractRoiFromCAM(fr.cam);
        if (box.score > 0.0f) {
            box.frameIdx = i;
            boxes.push_back(box);
        }
    }

    float avgFake   = (validCount > 0) ? totalFake / validCount : -1.0f;
    float avgReal   = (validCount > 0) ? totalReal / validCount : -1.0f;
    float inferTime = get_ms(inferStart);

    // NMS → 박스 1개
    float minx = -1, miny = -1, maxx = -1, maxy = -1;
    int   bestFrameIdx = 0;
    float bestCam[49]  = {};

    if (!boxes.empty()) {
        RoiBox best = applyNMS(boxes, 0.5f);
        minx = best.minx;  miny = best.miny;
        maxx = best.maxx;  maxy = best.maxy;
        bestFrameIdx = best.frameIdx;

        // 시각화를 위해 bestFrame의 CAM 재획득
        if (bestFrameIdx < (int)frames.size()) {
            auto fr = detector.inferWithCAM(frames[bestFrameIdx].data());
            if (fr.valid) std::copy(fr.cam, fr.cam + 49, bestCam);
        }
        LOGD("NMS result: box(%.1f,%.1f,%.1f,%.1f) score=%.3f frame=%d",
             minx, miny, maxx, maxy, best.score, bestFrameIdx);
    }

    // 결과 배열: [9 기본값] + [cam 49개] = 58개
    std::vector<float> results;
    results.reserve(58);
    for (float v : {avgFake, avgReal, detector.faceLoadTime, inferTime,
                    minx, miny, maxx, maxy, (float)bestFrameIdx})
        results.push_back(v);
    results.insert(results.end(), bestCam, bestCam + 49);

    jfloatArray resultMap = env->NewFloatArray(results.size());
    env->SetFloatArrayRegion(resultMap, 0, results.size(), results.data());
    return resultMap;
}

/**
 * CAM 시각화 (CamVisualizationActivity 에서 호출)
 * 지정 프레임 위에 CAM heatmap 오버레이 + bbox 그리기
 * 반환: JPEG 바이트 배열
 **/
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_shutter_1android_1ai_CamVisualizationActivity_renderCAMVisualization(
        JNIEnv* env,
        jobject /* this */,
        jstring videoPath, jint frameIdx, jfloatArray camData,
        jfloat minx, jfloat miny, jfloat maxx, jfloat maxy) {

    const char* vp = env->GetStringUTFChars(videoPath, nullptr);
    cv::VideoCapture cap(std::string(vp));
    env->ReleaseStringUTFChars(videoPath, vp);
    if (!cap.isOpened()) return nullptr;

    cap.set(cv::CAP_PROP_POS_FRAMES, frameIdx);
    cv::Mat frame;
    cap.read(frame);
    cap.release();
    if (frame.empty()) return nullptr;

    // 224x224 리사이즈 (BGR 유지)
    cv::Mat resized;
    cv::resize(frame, resized, cv::Size(224, 224));

    // CAM 7×7 → 0~1 정규화 → 224×224 업샘플
    jfloat* camPtr = env->GetFloatArrayElements(camData, nullptr);
    cv::Mat cam(7, 7, CV_32F, camPtr);
    double mn, mx;
    cv::minMaxLoc(cam, &mn, &mx);
    cv::Mat normCam = (mx - mn > 1e-6)
                    ? (cam - (float)mn) / (float)(mx - mn)
                    : cv::Mat::zeros(7, 7, CV_32F);
    cv::Mat camBig;
    cv::resize(normCam, camBig, cv::Size(224, 224), 0, 0, cv::INTER_LINEAR);
    env->ReleaseFloatArrayElements(camData, camPtr, JNI_ABORT);

    // COLORMAP_JET 적용
    cv::Mat camU8, heatmap;
    camBig.convertTo(camU8, CV_8U, 255.0);
    cv::applyColorMap(camU8, heatmap, cv::COLORMAP_JET);

    // 프레임 + 히트맵 블렌딩 (alpha 조정 가능)
    const float ALPHA = 0.5f;
    cv::Mat blended;
    cv::addWeighted(resized, 1.0f - ALPHA, heatmap, ALPHA, 0.0, blended);

    // Bounding box 그리기
    if (minx >= 0 && miny >= 0 && maxx > minx && maxy > miny) {
        cv::rectangle(blended,
                      cv::Point((int)minx, (int)miny),
                      cv::Point((int)maxx, (int)maxy),
                      cv::Scalar(0, 255, 0), 2);
    }

    // JPEG 인코딩 후 반환
    std::vector<uchar> buf;
    cv::imencode(".jpg", blended, buf);
    jbyteArray out = env->NewByteArray((jsize)buf.size());
    env->SetByteArrayRegion(out, 0, (jsize)buf.size(), (jbyte*)buf.data());
    return out;
}
