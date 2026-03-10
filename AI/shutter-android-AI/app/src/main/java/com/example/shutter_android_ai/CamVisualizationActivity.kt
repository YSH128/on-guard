package com.example.shutter_android_ai

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class CamVisualizationActivity : AppCompatActivity() {

    // native-lib.cpp 의 renderCAMVisualization 함수 호출
    // 반환: JPEG 바이트 배열 (실패 시 null)
    private external fun renderCAMVisualization(
        videoPath: String, frameIdx: Int, camData: FloatArray,
        minx: Float, miny: Float, maxx: Float, maxy: Float
    ): ByteArray?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cam_visualization)

        val ivCamResult = findViewById<ImageView>(R.id.ivCamResult)
        val tvBboxInfo  = findViewById<TextView>(R.id.tvBboxInfo)
        val btnBack     = findViewById<Button>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        val results   = MainActivity.lastResults
        val videoPath = MainActivity.lastVideoPath

        // 반환 형식: [4 + frameCount*2 + 54] = 78개 (frameCount=10 기준)
        // [0..3]: avgFake, avgReal, loadTime, inferTime
        // [4..23]: frame별 fake/real (10프레임 × 2)
        // [24..28]: minx, miny, maxx, maxy, bestFrameIdx
        // [29..77]: cam[49]
        val frameCount  = 10
        val baseOffset  = 4 + frameCount * 2   // = 24
        val minResultSize = baseOffset + 54    // = 78

        if (results.size < minResultSize || videoPath.isEmpty()) {
            tvBboxInfo.text = "결과 없음: 먼저 페이스 감지를 실행하세요"
            return
        }

        val minx         = results[baseOffset]
        val miny         = results[baseOffset + 1]
        val maxx         = results[baseOffset + 2]
        val maxy         = results[baseOffset + 3]
        val bestFrameIdx = results[baseOffset + 4].toInt()
        val camData      = results.sliceArray(baseOffset + 5 until baseOffset + 54)

        tvBboxInfo.text = "ROI: (%.1f, %.1f) ~ (%.1f, %.1f)  frame=%d".format(
            minx, miny, maxx, maxy, bestFrameIdx
        )

        thread {
            val jpegBytes = renderCAMVisualization(
                videoPath, bestFrameIdx, camData,
                minx, miny, maxx, maxy
            )
            if (jpegBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                runOnUiThread { ivCamResult.setImageBitmap(bitmap) }
            } else {
                runOnUiThread { tvBboxInfo.text = "렌더링 실패" }
            }
        }
    }
}
