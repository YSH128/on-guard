#ifndef QNN_MODEL_LOADER_H
#define QNN_MODEL_LOADER_H

#include <string>
#include <vector>
#include <mutex>
#include <dlfcn.h>
#include <android/log.h>
#include "QNN/QnnInterface.h"

// 백엔드 라이브러리 경로 (Android 환경에 맞게 수정 필요)
#define LIB_QNN_HTP "libQnnHtp.so"
#define LIB_QNN_GPU "libQnnGpu.so"
#define LIB_QNN_CPU "libQnnCpu.so"
#define LIB_QNN_SYSTEM "libQnnSystem.so"
#define LIB_FACE_DEEPFAKE "libface_deepfake.so"

class QnnModelLoader {
public:
    QnnModelLoader();
    ~QnnModelLoader();

    // Initialize the QNN system (Backend, Device)
    bool InitSystem(const std::string& cacheDir, const std::string& nativeLibDir);

    // Compile the graph only and save to cache (no model holding in memory)
    bool BuildModelCache(const std::string& assetsPath);

    // Load the model into memory (Context, Graph)
    bool LoadModel(const std::string& assetsPath);

    // QNN 그래프 실행 함수
    bool ExecuteGraph(const float* inputData, size_t inputElements,
                      float* logitsData, size_t logitsElements,
                      float* camData, size_t camElements);
    
    // 리소스 정리 함수 (JNI Release 시 호출)
    void Cleanup();

private:
    std::mutex m_mutex;
    
    void* m_backendHandle;
    void* m_systemHandle;
    void* m_modelHandle;

    std::string m_activeBackendName;

    // QNN 핸들
    Qnn_BackendHandle_t m_backend;
    Qnn_ContextHandle_t m_context;
    Qnn_GraphHandle_t m_graph;
    Qnn_DeviceHandle_t m_device;
    Qnn_LogHandle_t m_logger;

    // 함수 포인터 타입 정의 (dlopen / dlsym 에 사용)
    typedef Qnn_ErrorHandle_t (*QnnBackend_CreateFn_t)(Qnn_LogHandle_t, const QnnBackend_Config_t**, Qnn_BackendHandle_t*);
    typedef Qnn_ErrorHandle_t (*QnnDevice_CreateFn_t)(Qnn_LogHandle_t, const QnnDevice_Config_t**, Qnn_DeviceHandle_t*);
    typedef Qnn_ErrorHandle_t (*QnnContext_CreateFn_t)(Qnn_BackendHandle_t, Qnn_DeviceHandle_t, const QnnContext_Config_t**, Qnn_ContextHandle_t*);
    typedef Qnn_ErrorHandle_t (*QnnContext_CreateFromBinaryFn_t)(Qnn_BackendHandle_t, Qnn_DeviceHandle_t, const QnnContext_Config_t**, const void*, uint64_t, Qnn_ContextHandle_t*, Qnn_ProfileHandle_t);
    typedef Qnn_ErrorHandle_t (*QnnGraph_RetrieveFn_t)(Qnn_ContextHandle_t, const char*, Qnn_GraphHandle_t*);
    typedef Qnn_ErrorHandle_t (*QnnGraph_FinalizeFn_t)(Qnn_GraphHandle_t, Qnn_ProfileHandle_t, Qnn_SignalHandle_t);
    typedef Qnn_ErrorHandle_t (*QnnGraph_ExecuteFn_t)(Qnn_GraphHandle_t, const Qnn_Tensor_t*, uint32_t, Qnn_Tensor_t*, uint32_t, Qnn_ProfileHandle_t, Qnn_SignalHandle_t);
    typedef Qnn_ErrorHandle_t (*QnnContext_GetBinarySizeFn_t)(Qnn_ContextHandle_t, uint64_t*);
    typedef Qnn_ErrorHandle_t (*QnnContext_GetBinaryFn_t)(Qnn_ContextHandle_t, void*, uint64_t, uint64_t*);
    typedef Qnn_ErrorHandle_t (*QnnContext_FreeFn_t)(Qnn_ContextHandle_t, Qnn_ProfileHandle_t);
    typedef Qnn_ErrorHandle_t (*QnnBackend_FreeFn_t)(Qnn_BackendHandle_t);
    typedef Qnn_ErrorHandle_t (*QnnDevice_FreeFn_t)(Qnn_DeviceHandle_t);
    typedef Qnn_ErrorHandle_t (*QnnLog_FreeFn_t)(Qnn_LogHandle_t);
    
    // 모델 라이브러리(libface_deepfake.so)에서 가져올 ComposeGraph 함수 포인터
    typedef uint32_t (*QnnModel_ComposeGraphsFn_t)(
        Qnn_BackendHandle_t backendHandle,
        QNN_INTERFACE_VER_TYPE interface,
        Qnn_ContextHandle_t contextHandle,
        const void** graphsConfigInfo,
        const uint32_t numGraphsConfigInfo,
        void*** graphsInfo,
        uint32_t* numGraphsInfo,
        bool debug,
        QnnLog_Callback_t logCallback,
        QnnLog_Level_t maxLogLevel);

    // 인터페이스 원본 저장
    QNN_INTERFACE_VER_TYPE m_qnnInterface;

    // 포인터 저장 멤버
    QnnBackend_CreateFn_t m_fpBackendCreate;
    QnnDevice_CreateFn_t m_fpDeviceCreate;
    QnnContext_CreateFn_t m_fpContextCreate;
    QnnContext_CreateFromBinaryFn_t m_fpContextCreateFromBinary;
    QnnGraph_RetrieveFn_t m_fpGraphRetrieve;
    QnnGraph_FinalizeFn_t m_fpGraphFinalize;
    QnnGraph_ExecuteFn_t m_fpGraphExecute;
    QnnContext_GetBinarySizeFn_t m_fpContextGetBinarySize;
    QnnContext_GetBinaryFn_t m_fpContextGetBinary;
    QnnContext_FreeFn_t m_fpContextFree;
    QnnBackend_FreeFn_t m_fpBackendFree;
    QnnDevice_FreeFn_t m_fpDeviceFree;
    QnnLog_FreeFn_t m_fpLogFree;
    QnnModel_ComposeGraphsFn_t m_fpModelComposeGraphs;

public:
    // cam 크기 (native-lib.cpp에서 동적 버퍼 할당에 사용)
    uint32_t m_camH = 0;
    uint32_t m_camW = 0;

private:
    // Tensor IDs to dynamically cache and use
    uint32_t m_inputId;
    uint32_t m_logitsId;
    uint32_t m_camId;

    // 내부 유틸
    bool TryLoadBackend(const char* libName);
    bool LoadFunctionPointers();
    bool LoadModelLibrary();
    bool SetupBackendOnly(const std::string& backendName, const std::string& libName);
    bool CheckCacheExists(const std::string& cachePath);
    bool LoadFromCache(const std::string& cachePath);
    bool BuildAndCacheGraph(const std::string& cachePath, const std::string& assetsPath);
    std::vector<uint8_t> ReadBinaryFile(const std::string& filePath);
    void CleanupInternal();

    std::string m_cacheDir;
};

#endif // QNN_MODEL_LOADER_H
