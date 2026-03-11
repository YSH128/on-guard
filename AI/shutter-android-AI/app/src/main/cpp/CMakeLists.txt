# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("shutter_android_ai")

# CMakeLists.txt 파일 상단 혹은 중간에 추가
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -v")

# 1. 내 프로젝트의 기본 라이브러리 타겟 생성
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp)

# 2. 루트 경로 설정
set(LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../build/native_libs)
set(PYTORCH_DIR ${LIBS_DIR}/pytorch)
set(OPENCV_DIR ${LIBS_DIR}/opencv)

# 3. OpenCV 헤더 경로 탐색
set(OPENCV_INCLUDE_DIR ${OPENCV_DIR}/sdk/native/jni/include)
if(NOT EXISTS ${OPENCV_INCLUDE_DIR})
    set(OPENCV_INCLUDE_DIR ${OPENCV_DIR}/jni/include)
endif()

# 4. 인클루드 경로 등록
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${PYTORCH_DIR}/headers
    ${OPENCV_INCLUDE_DIR}
)

# 5. 라이브러리 파일(.so) 연결
add_library(pytorch_jni SHARED IMPORTED)
set_target_properties(pytorch_jni PROPERTIES
    IMPORTED_LOCATION ${PYTORCH_DIR}/jni/${ANDROID_ABI}/libpytorch_jni.so
)

add_library(fbjni SHARED IMPORTED)
set_target_properties(fbjni PROPERTIES IMPORTED_LOCATION ${PYTORCH_DIR}/jni/${ANDROID_ABI}/libfbjni.so)

add_library(c++_shared SHARED IMPORTED)
set_target_properties(c++_shared PROPERTIES IMPORTED_LOCATION
        ${PYTORCH_DIR}/jni/${ANDROID_ABI}/libc++_shared.so)

add_library(opencv_java SHARED IMPORTED)
if(EXISTS ${OPENCV_DIR}/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so)
    set_target_properties(opencv_java PROPERTIES
        IMPORTED_LOCATION ${OPENCV_DIR}/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so
    )
else()
    set_target_properties(opencv_java PROPERTIES
        IMPORTED_LOCATION ${OPENCV_DIR}/jni/${ANDROID_ABI}/libopencv_java4.so
    )
endif()

# 6. 최종 링크
target_link_libraries(${CMAKE_PROJECT_NAME}
    pytorch_jni
    fbjni
    c++_shared
    opencv_java
    android
    log
)