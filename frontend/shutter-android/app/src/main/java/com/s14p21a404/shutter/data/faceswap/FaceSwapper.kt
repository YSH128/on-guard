package com.s14p21a404.shutter.data.faceswap

import android.graphics.Bitmap

/**
 * 온디바이스 얼굴 합성 인터페이스
 *
 * 구현체는 ONNX Runtime 또는 PyTorch Mobile 모델을 로드해 프레임마다 얼굴 합성을 수행한다.
 */
interface FaceSwapper {

    /** 모델 초기화 및 준비. 백그라운드에서 호출해야 한다. */
    fun prepare()

    /**
     * 대상 얼굴 설정.
     * @param faceBitmap 합성할 얼굴 사진 (타겟 얼굴)
     */
    fun setTargetFace(faceBitmap: Bitmap)

    /**
     * 프레임에 얼굴 합성 적용.
     * @param frameBitmap 카메라 원본 프레임 (ARGB_8888)
     * @return 얼굴이 합성된 프레임. 대상 얼굴이 설정되지 않은 경우 원본 반환.
     */
    fun swapFrame(frameBitmap: Bitmap): Bitmap

    /** 모델이 준비 완료되었는지 여부 */
    val isReady: Boolean

    /** 대상 얼굴이 설정되었는지 여부 */
    val hasTargetFace: Boolean

    /** 리소스 해제 */
    fun release()
}