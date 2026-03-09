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

        if (results.size < 58 || videoPath.isEmpty()) {
            tvBboxInfo.text = "결과 없음: 먼저 페이스 감지를 실행하세요"
            return
        }

        val minx         = results[4]
        val miny         = results[5]
        val maxx         = results[6]
        val maxy         = results[7]
        val bestFrameIdx = results[8].toInt()
        val camData      = results.sliceArray(9..57)

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
