package com.s14p21a404.shutter.data.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.s14p21a404.shutter.data.detector.DeepfakeDetector

/**
 * 영상통화 화면 캡처 매니저
 *
 * MediaProjection + ImageReader를 사용하여 화면 프레임을 캡처하고,
 * RGBA byte[] (buffer)로 추출하여 등록된 Detector들에 직접 전달한다.
 *
 * 향후 OCR 등 다른 Detector가 추가되면 detectors 리스트에 함께 넘기면 된다.
 *
 * @param context          앱 Context (화면 해상도·DPI 획득용)
 * @param mediaProjection  Activity에서 사용자 동의 후 획득한 MediaProjection
 * @param detectors        캡처된 프레임을 전달할 Detector 목록
 * @param sampleIntervalMs 프레임 샘플링 간격 ms (기본 2000 = 2초)
 */
class ScreenCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val detectors: List<DeepfakeDetector>,
    private val sampleIntervalMs: Long = 500L
) {
    // 기기 실제 화면 해상도·DPI
    private val displayMetrics = context.resources.displayMetrics
    private val captureWidth = displayMetrics.widthPixels
    private val captureHeight = displayMetrics.heightPixels
    private val densityDpi = displayMetrics.densityDpi
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastCaptureTime = 0L

    @Volatile
    var isCapturing = false
        private set

    /** 일시정지 상태: true이면 프레임 수신은 하되 Detector에 전달하지 않는다. */
    @Volatile
    var isPaused = false
        private set

    // 최신 프레임 Bitmap 보존 (roi 크롭 오버레이용)
    @Volatile
    var latestBitmap: Bitmap? = null
        private set

    /**
     * 화면 캡처 시작
     */
    fun startCapture() {
        if (isCapturing) {
            Log.w(TAG, "이미 캡처 중")
            return
        }
        isCapturing = true

        // 백그라운드 스레드 생성
        handlerThread = HandlerThread("ScreenCaptureThread").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        // ImageReader 생성 (RGBA_8888)
        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2  // maxImages: 더블 버퍼링
        )

        // 프레임 수신 콜백
        imageReader!!.setOnImageAvailableListener({ reader ->
            if (!isCapturing) return@setOnImageAvailableListener

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            // 일시정지 중이면 이미지만 소비하고 스킵
            if (isPaused) {
                image.close()
                return@setOnImageAvailableListener
            }

            try {
                // 샘플링: 간격 미달이면 스킵
                val now = SystemClock.elapsedRealtime()
                if (now - lastCaptureTime < sampleIntervalMs) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                lastCaptureTime = now

                val plane = image.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val width = image.width
                val height = image.height
                val rowBytes = width * pixelStride

                // ByteBuffer → byte[] 복사 (JNI 전달용, padding 포함)
                buffer.rewind()
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                // Bitmap 생성 (roi 크롭 오버레이용)
                // rowStride에 padding이 있으면 제거하여 clean buffer 생성
                val bitmap: Bitmap
                if (rowStride == rowBytes) {
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    buffer.rewind()
                    bitmap.copyPixelsFromBuffer(buffer)
                } else {
                    // padding 제거: 행마다 실제 픽셀 부분만 추출
                    val cleanBytes = ByteArray(rowBytes * height)
                    for (y in 0 until height) {
                        System.arraycopy(bytes, y * rowStride, cleanBytes, y * rowBytes, rowBytes)
                    }
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(cleanBytes))
                }
                // 이전 Bitmap 해제 (메모리 누수 방지)
                latestBitmap?.recycle()
                latestBitmap = bitmap

                Log.d(TAG, "프레임 캡처: ${width}x${height}, stride=$rowStride, bytes=${bytes.size}")

                // pauseCapture()가 프레임 처리 도중에 호출된 경우 Detector 전달을 차단
                if (isPaused) {
                    Log.d(TAG, "프레임 캡처 후 isPaused 감지 → Detector 전달 스킵")
                    return@setOnImageAvailableListener
                }

                // 등록된 Detector들에 프레임 전달
                detectors.forEach { it.processFrame(bytes, width, height, rowStride) }

            } catch (e: Exception) {
                Log.e(TAG, "프레임 처리 실패", e)
            } finally {
                image.close()
            }
        }, handler)

        // MediaProjection 종료 콜백 (Android 14+: createVirtualDisplay 전에 반드시 등록해야 함)
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection 종료됨")
                stopCapture()
            }
        }, handler)

        // VirtualDisplay 생성 → ImageReader의 Surface에 화면 출력
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ShutterScreenCapture",
            captureWidth,
            captureHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )

        Log.d(TAG, "화면 캡처 시작: ${captureWidth}x${captureHeight}, 간격=${sampleIntervalMs}ms")
    }

    /**
     * 캡처 일시정지: VirtualDisplay·MediaProjection은 유지하고 프레임 처리만 중단한다.
     * 영상통화 종료 시 호출. 다음 통화에서 resumeCapture()로 바로 재개 가능.
     */
    fun pauseCapture() {
        isPaused = true
        latestBitmap?.recycle()
        latestBitmap = null
        Log.d(TAG, "화면 캡처 일시정지")
    }

    /**
     * 캡처 재개: 일시정지 상태를 해제하여 다시 프레임을 Detector에 전달한다.
     */
    fun resumeCapture() {
        isPaused = false
        lastCaptureTime = 0L   // 재개 즉시 첫 프레임을 처리하도록 리셋
        Log.d(TAG, "화면 캡처 재개")
    }

    /**
     * 화면 캡처 중단 + 리소스 정리
     */
    fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        latestBitmap?.recycle()
        latestBitmap = null

        Log.d(TAG, "화면 캡처 중단")
    }

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }
}

