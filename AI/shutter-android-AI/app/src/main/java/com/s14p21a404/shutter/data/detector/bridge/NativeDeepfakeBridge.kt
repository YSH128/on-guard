package com.s14p21a404.shutter.data.detector.bridge

class NativeDeepfakeBridge {

    /**
     * 딥페이크 탐지 모델을 로드합니다.
     * @return FloatArray[2] = [success(1.0f/0.0f), loadTimeMs]
     */
    external fun loadModels(faceModelPath: String): FloatArray

    /**
     * 단일 프레임에 대해 딥페이크 탐지를 실행합니다.
     * @param buffer RGBA 포맷의 이미지 바이트 배열
     * @param width  이미지 너비 (px)
     * @param height 이미지 높이 (px)
     * @param stride 한 행의 바이트 수 (= width * 4)
     * @return FloatArray[4 + camSize + 5]:
     *   [0] fakeProb, [1] realProb, [2] modelLoadTimeMs, [3] inferTimeMs,
     *   [4 .. 4+camSize-1] CAM 데이터 (7×7=49),
     *   [4+camSize] minx, [+1] miny, [+2] maxx, [+3] maxy, [+4] bboxScore
     */
    external fun detectDeepfakeVisual(
        buffer: ByteArray,
        width: Int,
        height: Int,
        stride: Int
    ): FloatArray

    companion object {
        init {
            System.loadLibrary("shutter_android_ai")
        }
    }
}
