cmake_minimum_required(VERSION 3.22.1)
project("shutter-native")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -v")

# 1. 라이브러리 타겟 생성
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
        QnnModelLoader.cpp)

# 2. 경로 설정
set(LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../build/native_libs)
set(PYTORCH_DIR ${LIBS_DIR}/pytorch)
set(OPENCV_DIR ${LIBS_DIR}/opencv)
set(ORT_DIR ${LIBS_DIR}/onnxruntime)

# 3. OpenCV 헤더 경로
set(OPENCV_INCLUDE_DIR ${OPENCV_DIR}/sdk/native/jni/include)
if(NOT EXISTS ${OPENCV_INCLUDE_DIR})
    set(OPENCV_INCLUDE_DIR ${OPENCV_DIR}/jni/include)
endif()

# 4. 인클루드 경로 등록
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${PYTORCH_DIR}/headers
    ${OPENCV_INCLUDE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/include/QNN
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${ORT_DIR}/headers  # 💡 여기에 nnapi_provider_factory.h가 들어있어야 합니다.
)

# 5. 라이브러리 파일(.so) 연결
add_library(pytorch_jni SHARED IMPORTED)
set_target_properties(pytorch_jni PROPERTIES
    IMPORTED_LOCATION ${PYTORCH_DIR}/jni/${ANDROID_ABI}/libpytorch_jni.so)

add_library(fbjni SHARED IMPORTED)
set_target_properties(fbjni PROPERTIES IMPORTED_LOCATION ${PYTORCH_DIR}/jni/${ANDROID_ABI}/libfbjni.so)

add_library(c++_shared SHARED IMPORTED)
set_target_properties(c++_shared PROPERTIES IMPORTED_LOCATION
        ${PYTORCH_DIR}/jni/${ANDROID_ABI}/libc++_shared.so)

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION ${ORT_DIR}/jni/${ANDROID_ABI}/libonnxruntime.so)

add_library(opencv_java SHARED IMPORTED)
if(EXISTS ${OPENCV_DIR}/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so)
    set_target_properties(opencv_java PROPERTIES
        IMPORTED_LOCATION ${OPENCV_DIR}/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so)
else()
    set_target_properties(opencv_java PROPERTIES
        IMPORTED_LOCATION ${OPENCV_DIR}/jni/${ANDROID_ABI}/libopencv_java4.so)
endif()

# 6. 최종 링크 (💡 nativewindow 라이브러리 추가 필수!)
target_link_libraries(${CMAKE_PROJECT_NAME}
    pytorch_jni
    fbjni
    c++_shared
    opencv_java
    onnxruntime
    android
    log
    nativewindow  # 💡 NNAPI 가속 사용 시 안드로이드 그래픽 버퍼 처리를 위해 필요합니다.
)