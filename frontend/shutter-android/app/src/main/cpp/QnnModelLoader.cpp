#include "QnnModelLoader.h"
#include <fstream>
#include <android/log.h>
#include <QNN/QnnInterface.h>

#define LOG_TAG "QnnModelLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void EmptyLogCallback(const char* fmt, QnnLog_Level_t level, uint64_t timestamp, va_list args) {
    // Empty callback to completely suppress QNN internal logs
}

typedef struct {
  Qnn_GraphHandle_t graph;
  char* graphName;
  Qnn_Tensor_t* inputTensors;
  uint32_t numInputTensors;
  Qnn_Tensor_t* outputTensors;
  uint32_t numOutputTensors;
} GraphInfo_t;

QnnModelLoader::QnnModelLoader() 
    : m_backendHandle(nullptr), m_systemHandle(nullptr), m_modelHandle(nullptr),
      m_backend(nullptr), m_context(nullptr), m_graph(nullptr), m_device(nullptr), m_logger(nullptr),
      m_inputId(0), m_logitsId(0), m_camId(0), m_camH(0), m_camW(0),
      m_fpBackendCreate(nullptr), m_fpDeviceCreate(nullptr),
      m_fpContextCreateFromBinary(nullptr), m_fpContextGetBinarySize(nullptr),
      m_fpContextGetBinary(nullptr), m_fpContextFree(nullptr),
      m_fpBackendFree(nullptr), m_fpDeviceFree(nullptr), m_fpLogFree(nullptr),
      m_fpGraphFinalize(nullptr), m_fpModelComposeGraphs(nullptr) {}

QnnModelLoader::~QnnModelLoader() {
    Cleanup();
}

void QnnModelLoader::Cleanup() {
    std::lock_guard<std::mutex> lock(m_mutex);
    CleanupInternal();
}

void QnnModelLoader::CleanupInternal() {
    if (m_context && m_fpContextFree) {
        m_fpContextFree(m_context, nullptr);
        m_context = nullptr;
    }
    
    if (m_device && m_fpDeviceFree) {
        m_fpDeviceFree(m_device);
        m_device = nullptr;
    }

    if (m_backend && m_fpBackendFree) {
        m_fpBackendFree(m_backend);
        m_backend = nullptr;
    }

    if (m_logger && m_fpLogFree) {
        m_fpLogFree(m_logger);
        m_logger = nullptr;
    }

    if (m_modelHandle) {
        dlclose(m_modelHandle);
        m_modelHandle = nullptr;
    }
    
    if (m_systemHandle) {
        dlclose(m_systemHandle);
        m_systemHandle = nullptr;
    }
    
    if (m_backendHandle) {
        dlclose(m_backendHandle);
        m_backendHandle = nullptr;
    }
}

bool QnnModelLoader::TryLoadBackend(const char* libName) {
    m_backendHandle = dlopen(libName, RTLD_NOW | RTLD_LOCAL);
    return m_backendHandle != nullptr;
}

bool QnnModelLoader::LoadFunctionPointers() {
    typedef Qnn_ErrorHandle_t (*QnnInterface_GetProvidersFn_t)(const QnnInterface_t***, uint32_t*);
    auto getProvidersFn = (QnnInterface_GetProvidersFn_t)dlsym(m_backendHandle, "QnnInterface_getProviders");
    
    if (!getProvidersFn) {
        LOGE("Failed to load QnnInterface_getProviders from backend library.");
        return false;
    }

    const QnnInterface_t** providerList = nullptr;
    uint32_t numProviders = 0;
    if (getProvidersFn(&providerList, &numProviders) != QNN_SUCCESS || numProviders == 0) {
        LOGE("Failed to get QNN interface providers.");
        return false;
    }

    const auto& qnnInterface = providerList[0]->QNN_INTERFACE_VER_NAME;
    m_qnnInterface = qnnInterface; // Store exactly for model generation

    m_fpBackendCreate = m_qnnInterface.backendCreate;
    m_fpDeviceCreate = m_qnnInterface.deviceCreate;
    m_fpContextCreate = qnnInterface.contextCreate;
    m_fpContextCreateFromBinary = qnnInterface.contextCreateFromBinary;
    m_fpGraphRetrieve = qnnInterface.graphRetrieve;
    m_fpGraphFinalize = qnnInterface.graphFinalize;
    m_fpGraphExecute = qnnInterface.graphExecute;
    m_fpContextGetBinarySize = qnnInterface.contextGetBinarySize;
    m_fpContextGetBinary = qnnInterface.contextGetBinary;
    m_fpContextFree = qnnInterface.contextFree;
    m_fpBackendFree = qnnInterface.backendFree;
    m_fpDeviceFree = qnnInterface.deviceFree;

    if (qnnInterface.logCreate) {
        qnnInterface.logCreate(EmptyLogCallback, QNN_LOG_LEVEL_ERROR, &m_logger);
        m_fpLogFree = qnnInterface.logFree;
    }

    if (!m_fpBackendCreate || !m_fpContextCreateFromBinary || !m_fpGraphExecute) {
        LOGE("QNN Provider does not support required functions.");
        return false;
    }

    // 시스템 핸들 (QnnSystem.so 옵션)
    m_systemHandle = dlopen(LIB_QNN_SYSTEM, RTLD_NOW | RTLD_LOCAL);
    if (!m_systemHandle) {
        LOGE("QNN System context library not found: %s. Error: %s", LIB_QNN_SYSTEM, dlerror());
    }

    return true;
}

bool QnnModelLoader::LoadModelLibrary() {
    if (m_modelHandle) return true;
    m_modelHandle = dlopen(LIB_FACE_DEEPFAKE, RTLD_NOW | RTLD_LOCAL);
    if (m_modelHandle) {
        m_fpModelComposeGraphs = (QnnModel_ComposeGraphsFn_t)dlsym(m_modelHandle, "QnnModel_composeGraphs");
        if (!m_fpModelComposeGraphs) {
            LOGE("Failed to find QnnModel_composeGraphs in %s", LIB_FACE_DEEPFAKE);
            return false;
        }
    } else {
        LOGE("Failed to load model library: %s", LIB_FACE_DEEPFAKE);
        return false;
    }
    return true;
}

std::vector<uint8_t> QnnModelLoader::ReadBinaryFile(const std::string& filePath) {
    std::ifstream file(filePath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return {};

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    std::vector<uint8_t> buffer(size);
    if (file.read((char*)buffer.data(), size)) {
        return buffer;
    }
    return {};
}

bool QnnModelLoader::SetupBackendOnly(const std::string& backendName, const std::string& libName) {
    if (!TryLoadBackend(libName.c_str())) {
        LOGE("Failed to load backend library: %s", libName.c_str());
        return false;
    }

    if (!LoadFunctionPointers()) {
        return false;
    }

    if (m_fpBackendCreate(nullptr, nullptr, &m_backend) != QNN_SUCCESS) {
        LOGE("QnnBackend_create failed for %s.", backendName.c_str());
        return false;
    }

    if (m_fpDeviceCreate) {
        m_fpDeviceCreate(nullptr, nullptr, &m_device);
    }

    m_activeBackendName = backendName;
    return true;
}

bool QnnModelLoader::InitSystem(const std::string& cacheDir, const std::string& nativeLibDir) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    // [필수] Android 앱 샌드박스에서 QNN HTP Skel 라이브러리를 로딩하기 위해
    // ADSP_LIBRARY_PATH에 앱의 nativeLibraryDir을 최우선으로 주입합니다.
    // 이것이 없으면 FastRPC의 apps_std_fopen_with_env가 libQnnHtpV68Skel.so를 찾지 못합니다.
    std::string adspPath = nativeLibDir + ";/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp";
    setenv("ADSP_LIBRARY_PATH", adspPath.c_str(), 1); // 1 = overwrite
    LOGI("ADSP_LIBRARY_PATH set to: %s", adspPath.c_str());

    if (m_backendHandle != nullptr && m_backend != nullptr) {
        LOGI("QNN System already initialized with %s", m_activeBackendName.c_str());
        return true;
    }

    m_cacheDir = cacheDir;
    
    std::string configPath = cacheDir + "/active_backend.txt";
    std::string savedBackend = "";
    std::ifstream infile(configPath);
    if (infile.good()) {
         infile >> savedBackend;
         infile.close();
    }

    struct BackendTarget {
        std::string name;
        std::string lib;
    };

    std::vector<BackendTarget> targets = {
        {"htp", LIB_QNN_HTP},
        {"gpu", LIB_QNN_GPU},
        {"cpu", LIB_QNN_CPU}
    };

    // If we have a saved backend, try it first
    if (!savedBackend.empty()) {
        auto it = std::find_if(targets.begin(), targets.end(), [&](const BackendTarget& t) { return t.name == savedBackend; });
        if (it != targets.end()) {
            BackendTarget savedTarget = *it;
            targets.erase(it);
            targets.insert(targets.begin(), savedTarget);
        }
    }

    bool success = false;
    for (const auto& target : targets) {
        LOGI("--- Attempting to initialize backend: %s ---", target.name.c_str());
        if (SetupBackendOnly(target.name, target.lib)) {
            success = true;
            m_activeBackendName = target.name;
            break;
        } else {
            LOGE("Backend %s failed complete initialization. Cleaning up to try next.", target.name.c_str());
            CleanupInternal(); // reset state for next attempt WITHOUT deadlocking
        }
    }

    if (success) {
        std::ofstream outfile(configPath, std::ios::trunc);
        if (outfile.good()) {
             outfile << m_activeBackendName;
             outfile.close();
        }
        LOGI("=== Final Active QNN Backend: %s ===", m_activeBackendName.c_str());
        return true;
    }

    LOGE("CRITICAL: All QNN Backends failed to initialize and build graphs!");
    return false;
}

bool QnnModelLoader::LoadModel(const std::string& assetsPath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_graph != nullptr) {
        LOGI("Model already loaded and graph is active! Skipping reload.");
        return true;
    }

    if (!m_backend) {
        LOGE("Cannot load model. Backend is not initialized.");
        return false;
    }
    
    std::string cachePath = m_cacheDir + "/" + m_activeBackendName + "_deepfake_cache.bin";

    if (LoadFromCache(cachePath)) {
        LOGI("Model successfully loaded from cache for %s", m_activeBackendName.c_str());
        return true;
    }

    LOGI("Cache not found in LoadModel! Fallback to building graph...");
    if (!LoadModelLibrary()) return false;
    if (BuildAndCacheGraph(cachePath, assetsPath)) {
        LOGI("Graph built and loaded for %s.", m_activeBackendName.c_str());
        return true;
    }
    return false;
}

bool QnnModelLoader::CheckCacheExists(const std::string& cachePath) {
    std::ifstream file(cachePath);
    return file.good();
}

bool QnnModelLoader::BuildModelCache(const std::string& assetsPath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_backend) return false;
    
    std::string cachePath = m_cacheDir + "/" + m_activeBackendName + "_deepfake_cache.bin";

    if (CheckCacheExists(cachePath)) {
        LOGI("Cache already exists for %s. Skipping build.", m_activeBackendName.c_str());
        return true;
    }

    if (!LoadModelLibrary()) return false;

    LOGI("Cache not found for %s. Building cache using ComposeGraphs...", m_activeBackendName.c_str());
    if (BuildAndCacheGraph(cachePath, assetsPath)) {
        LOGI("Graph built successfully. Releasing context to free memory.");
        // Free the context since we only wanted to compile the cache, not hold it in memory.
        if (m_context && m_fpContextFree) {
            m_fpContextFree(m_context, nullptr);
            m_context = nullptr;
            m_graph = nullptr;
        }
        return true;
    }
    return false;
}

bool QnnModelLoader::LoadFromCache(const std::string& cachePath) {
    std::string tensorCacheFile = cachePath + ".tensors";
    FILE* tf = fopen(tensorCacheFile.c_str(), "rb");
    if (tf) {
        fread(&m_inputId,  sizeof(uint32_t), 1, tf);
        fread(&m_logitsId, sizeof(uint32_t), 1, tf);
        fread(&m_camId,    sizeof(uint32_t), 1, tf);
        fread(&m_camH,     sizeof(uint32_t), 1, tf);
        fread(&m_camW,     sizeof(uint32_t), 1, tf);
        fclose(tf);
        LOGI("Loaded tensor IDs: In=%u, Logits=%u, Cam=%u, CamH=%u, CamW=%u",
             m_inputId, m_logitsId, m_camId, m_camH, m_camW);
    } else {
        LOGE("Tensor cache missing. Fallback to ComposeGraphs.");
        return false;
    }

    std::vector<uint8_t> buffer = ReadBinaryFile(cachePath);
    if (buffer.empty()) return false;

    Qnn_ErrorHandle_t err = m_fpContextCreateFromBinary(
        m_backend, m_device, nullptr, buffer.data(), (uint64_t)buffer.size(), &m_context, nullptr
    );

    if (err == QNN_SUCCESS) {
        if (m_fpGraphRetrieve) {
            // Retrieve graph from context (Assume graph name is face_deepfake or _features)
            Qnn_ErrorHandle_t retrieveErr = m_fpGraphRetrieve(m_context, "face_deepfake", &m_graph);
            if (retrieveErr != QNN_SUCCESS) {
                m_fpGraphRetrieve(m_context, "model", &m_graph); // Alternative safe name
            }
        }
        
        // GPU 백엔드 등에서 바이너리 복원 후에도 Finalize가 다시 요구되는 경우가 있음
        if (m_graph && m_fpGraphFinalize) {
            Qnn_ErrorHandle_t finalizeErr = m_fpGraphFinalize(m_graph, nullptr, nullptr);
            if (finalizeErr != QNN_SUCCESS) {
                LOGE("QnnGraph_finalize failed after cache load with error: %llu", (unsigned long long)finalizeErr);
            } else {
                LOGI("Graph finalized successfully after cache load.");
            }
        }
    }

    return err == QNN_SUCCESS;
}

bool QnnModelLoader::BuildAndCacheGraph(const std::string& cachePath, const std::string& assetsPath) {
    if (!m_fpModelComposeGraphs) {
        LOGE("Cannot build graph: QnnModel_composeGraphs is null.");
        return false;
    }

    if (!m_fpContextCreate || m_fpContextCreate(m_backend, m_device, nullptr, &m_context) != QNN_SUCCESS) {
        LOGE("Failed to create Qnn Context for composing graphs.");
        return false;
    }

    void** graphsInfo = nullptr;
    uint32_t numGraphsInfo = 0;

    uint32_t err = m_fpModelComposeGraphs(
        m_backend,
        m_qnnInterface,
        m_context,
        nullptr,
        0,
        &graphsInfo,
        &numGraphsInfo,
        false,
        EmptyLogCallback,
        QNN_LOG_LEVEL_ERROR
    );

    if (err != 0) { // 0 is MODEL_NO_ERROR
        LOGE("QnnModel_composeGraphs failed with error code: %u", err);
        return false;
    }
    
    if (numGraphsInfo > 0 && graphsInfo) {
        GraphInfo_t* gInfo = ((GraphInfo_t**)graphsInfo)[0];
        m_graph = gInfo->graph; 
        if (gInfo->numInputTensors > 0) m_inputId  = gInfo->inputTensors[0].v2.id;
        if (gInfo->numOutputTensors > 0) m_logitsId = gInfo->outputTensors[0].v2.id;
        if (gInfo->numOutputTensors > 1) {
            m_camId = gInfo->outputTensors[1].v2.id;
            m_camH  = gInfo->outputTensors[1].v2.dimensions[0]; // 동적으로 읽기
            m_camW  = gInfo->outputTensors[1].v2.dimensions[1];
        }

        std::string tensorCacheFile = cachePath + ".tensors";
        FILE* tf = fopen(tensorCacheFile.c_str(), "wb");
        if (tf) {
            fwrite(&m_inputId,  sizeof(uint32_t), 1, tf);
            fwrite(&m_logitsId, sizeof(uint32_t), 1, tf);
            fwrite(&m_camId,    sizeof(uint32_t), 1, tf);
            fwrite(&m_camH,     sizeof(uint32_t), 1, tf);
            fwrite(&m_camW,     sizeof(uint32_t), 1, tf);
            fclose(tf);
            LOGI("Saved tensor IDs: In=%u, Logits=%u, Cam=%u, CamH=%u, CamW=%u",
                 m_inputId, m_logitsId, m_camId, m_camH, m_camW);
        }
    }

    if (m_graph && m_fpGraphFinalize) {
        Qnn_ErrorHandle_t finalizeErr = m_fpGraphFinalize(m_graph, nullptr, nullptr);
        if (finalizeErr != QNN_SUCCESS) {
            LOGE("QnnGraph_finalize failed with error: %llu", (unsigned long long)finalizeErr);
            return false;
        }
        LOGI("Graph finalized successfully.");
    }

    // 바이너리 캐싱
    uint64_t binarySize = 0;
    if (m_fpContextGetBinarySize && m_fpContextGetBinarySize(m_context, &binarySize) == QNN_SUCCESS) {
        std::vector<uint8_t> binaryBuffer(binarySize);
        if (m_fpContextGetBinary(m_context, binaryBuffer.data(), binarySize, nullptr) == QNN_SUCCESS) {
            std::ofstream file(cachePath, std::ios::binary | std::ios::trunc);
            if (file.is_open()) {
                file.write((char*)binaryBuffer.data(), binarySize);
                file.close();
                LOGI("Saved context binary to cache.");
                return true;
            }
        }
    }

    // 캐시에 기록하지 못했더라도 정상 로드 된 경우 true 리턴
    return true;
}

bool QnnModelLoader::ExecuteGraph(const float* inputData,  size_t inputElements,
                                  float* logitsData, size_t logitsElements,
                                  float* camData,    size_t camElements) {
    if (!m_graph || !m_fpGraphExecute) {
        LOGE("QNN Graph handle or Execute function is missing.");
        return false;
    }

    uint32_t dimensions_input[] = {1, 256, 256, 3};
    Qnn_Tensor_t inTensor = {QNN_TENSOR_VERSION_2, {}};
    inTensor.v2.id = m_inputId;
    inTensor.v2.name = "input";
    inTensor.v2.type = QNN_TENSOR_TYPE_APP_WRITE;
    inTensor.v2.dataFormat = QNN_TENSOR_DATA_FORMAT_DENSE;
    inTensor.v2.dataType = QNN_DATATYPE_FLOAT_32;
    inTensor.v2.rank = 4;
    inTensor.v2.dimensions = dimensions_input;
    inTensor.v2.memType = QNN_TENSORMEMTYPE_RAW;
    inTensor.v2.clientBuf.data = (void*)inputData;
    inTensor.v2.clientBuf.dataSize = inputElements * sizeof(float);

    uint32_t dimensions_logits[] = {1, 2};
    Qnn_Tensor_t logitsTensor = {QNN_TENSOR_VERSION_2, {}};
    logitsTensor.v2.id = m_logitsId;
    logitsTensor.v2.name = "logits";
    logitsTensor.v2.type = QNN_TENSOR_TYPE_APP_READ;
    logitsTensor.v2.dataFormat = QNN_TENSOR_DATA_FORMAT_DENSE;
    logitsTensor.v2.dataType = QNN_DATATYPE_FLOAT_32;
    logitsTensor.v2.rank = 2;
    logitsTensor.v2.dimensions = dimensions_logits;
    logitsTensor.v2.memType = QNN_TENSORMEMTYPE_RAW;
    logitsTensor.v2.clientBuf.data = (void*)logitsData;
    logitsTensor.v2.clientBuf.dataSize = logitsElements * sizeof(float);

    uint32_t dimensions_cam[] = {m_camH, m_camW}; // 동적
    Qnn_Tensor_t camTensor = {QNN_TENSOR_VERSION_2, {}};
    camTensor.v2.id = m_camId;
    camTensor.v2.name = "cam";
    camTensor.v2.type = QNN_TENSOR_TYPE_APP_READ;
    camTensor.v2.dataFormat = QNN_TENSOR_DATA_FORMAT_DENSE;
    camTensor.v2.dataType = QNN_DATATYPE_FLOAT_32;
    camTensor.v2.rank = 2;
    camTensor.v2.dimensions = dimensions_cam;
    camTensor.v2.memType = QNN_TENSORMEMTYPE_RAW;
    camTensor.v2.clientBuf.data = (void*)camData;
    camTensor.v2.clientBuf.dataSize = camElements * sizeof(float);

    Qnn_Tensor_t outTensors[] = {logitsTensor, camTensor};

    Qnn_ErrorHandle_t err = m_fpGraphExecute(m_graph, &inTensor, 1, outTensors, 2, nullptr, nullptr);
    if (err != QNN_SUCCESS) {
        LOGE("QnnGraph_execute failed with error: %llu", (unsigned long long)err);
        return false;
    }
    return true;
}
