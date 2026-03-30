package com.s14p21a404.shutter.data.detector.real

import android.content.Context
import android.util.Log
import com.s14p21a404.shutter.R
import com.s14p21a404.shutter.data.detector.DeepfakeDetector
import com.s14p21a404.shutter.domain.model.DeepfakeDetectionResult
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream

class OpenCvFaceTracker(private val context: Context) : DeepfakeDetector {
    private var faceCascade: CascadeClassifier? = null
    private var onFrameResultCallback: ((DeepfakeDetectionResult) -> Unit)? = null
    
    @Volatile
    var isTrackingEnabled = false

    @Volatile
    private var stopped = false

    init {
        initOpenCV()
    }

    private fun initOpenCV() {
        if (org.opencv.android.OpenCVLoader.initDebug()) {
            try {
                val cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE)
                val cascadeFile = File(cascadeDir, "haarcascade_frontalface_default.xml")
                if (!cascadeFile.exists()) {
                    val inputStream = context.resources.openRawResource(R.raw.haarcascade_frontalface_default)
                    val os = FileOutputStream(cascadeFile)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                    inputStream.close()
                    os.close()
                }
                faceCascade = CascadeClassifier(cascadeFile.absolutePath)
                if (faceCascade!!.empty()) {
                    Log.e(TAG, "Failed to load cascade classifier")
                    faceCascade = null
                } else {
                    Log.i(TAG, "Loaded cascade classifier from ${cascadeFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cascade file", e)
            }
        } else {
            Log.e(TAG, "OpenCVLoader.initDebug() failed")
        }
    }

    override fun startDetection(
        onResult: (DeepfakeDetectionResult) -> Unit,
        onFrameResult: (DeepfakeDetectionResult) -> Unit,
        onReady: () -> Unit
    ) {
        stopped = false
        onFrameResultCallback = onFrameResult
        isTrackingEnabled = false // UI 상에서 명시적으로 켤 때까지 대기
        onReady()
    }

    override fun processFrame(buffer: ByteArray, width: Int, height: Int, rowStride: Int) {
        if (stopped) return
        if (!isTrackingEnabled) return
        if (faceCascade == null || buffer.isEmpty()) return

        try {
            val cleanBytes = if (rowStride == width * 4) {
                buffer
            } else {
                val arr = ByteArray(width * 4 * height)
                for (y in 0 until height) {
                    System.arraycopy(buffer, y * rowStride, arr, y * width * 4, width * 4)
                }
                arr
            }

            val mat = Mat(height, width, CvType.CV_8UC4)
            mat.put(0, 0, cleanBytes)

            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.equalizeHist(grayMat, grayMat)

            val faces = MatOfRect()
            faceCascade!!.detectMultiScale(
                grayMat, faces, 1.1, 3, 0,
                Size(60.0, 60.0)
            )

            var opencvRoi: FloatArray? = null
            val faceArray = faces.toArray()
            if (faceArray.isNotEmpty()) {
                val largestFace = faceArray.maxByOrNull { it.width * it.height }
                if (largestFace != null) {
                    val faceWidthRatio = largestFace.width.toFloat() / width
                    val faceHeightRatio = largestFace.height.toFloat() / height
                    
                    // 얼굴이 프레임 가로의 15%, 세로의 10% 이상일 때만 박스를 침 (너무 작은 얼굴 무시)
                    if (faceWidthRatio >= 0.2f && faceHeightRatio >= 0.2f) {
                        opencvRoi = FloatArray(4)
                        opencvRoi[0] = largestFace.x.toFloat() / width
                        opencvRoi[1] = largestFace.y.toFloat() / height
                        opencvRoi[2] = (largestFace.x + largestFace.width).toFloat() / width
                        opencvRoi[3] = (largestFace.y + largestFace.height).toFloat() / height
                    }
                }
            }
            
            mat.release()
            grayMat.release()
            faces.release()

            if (opencvRoi != null) {
                val quickResult = DeepfakeDetectionResult(
                    detected = true, // 바텀시트가 떠있는 상태이므로 true로 전달하여 오버레이 유지
                    fakeProb = 1f,
                    roi = opencvRoi,
                    rawResultJson = "OpenCV tracking"
                )
                onFrameResultCallback?.invoke(quickResult)
            } else {
                // 얼굴이 검출되지 않았거나 기준보다 작으면 BBox 제거를 위해 빈 결과 전달
                val emptyResult = DeepfakeDetectionResult(
                    detected = true, // 오버레이 유지를 위해 true 유지
                    fakeProb = 0f,
                    roi = null,
                    rawResultJson = "OpenCV tracking - No Face"
                )
                onFrameResultCallback?.invoke(emptyResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV face detection failed", e)
        }
    }

    override fun stopDetection() {
        stopped = true
        onFrameResultCallback = null
        isTrackingEnabled = false
    }

    companion object {
        private const val TAG = "OpenCvFaceTracker"
    }
}
