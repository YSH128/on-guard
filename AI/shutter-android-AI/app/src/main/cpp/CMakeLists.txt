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

# 1. 내 프로젝트의 기본 라이브러리 타겟 생성
add_library(${CMAKE_PROJECT_NAME} SHARED
    native-lib.cpp)

# 2. 추출된 PyTorch 경로 변수 설정
set(PYTORCH_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../build/native_libs/pytorch)

# 3. PyTorch Full 버전 라이브러리 (libpytorch_jni.so) 가져오기
add_library(pytorch_jni SHARED IMPORTED)
set_target_properties(pytorch_jni PROPERTIES IMPORTED_LOCATION
        ${PYTORCH_DIR}/jni/${ANDROID_ABI}/libpytorch_jni.so)

# 4. fbjni 라이브러리 가져오기 (종속성)
add_library(fbjni SHARED IMPORTED)
set_target_properties(fbjni PROPERTIES IMPORTED_LOCATION
        ${PYTORCH_DIR}/jni/${ANDROID_ABI}/libfbjni.so)

# 5. 헤더 파일 경로 포함
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${PYTORCH_DIR}/headers)

# 6. 타겟 라이브러리에 외부 라이브러리 링크
target_link_libraries(${CMAKE_PROJECT_NAME}
    pytorch_jni
    fbjni
    android
    log)