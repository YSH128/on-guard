package com.s14p21a404.shutter.data.faceswap

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * inswapper_128.onnx 기반 온디바이스 페이스스왑
 *
 * 모델 스펙:
 *   입력 target  [1, 3, 128, 128] float32  — 교체될 얼굴 크롭, 정규화 (x/255-0.5)/0.5 → [-1,1]
 *   입력 source  [1, 512]         float32  — 주입할 아이덴티티 임베딩 (ArcFace L2 정규화)
 *   출력 output  [1, 3, 128, 128] float32  — 합성 결과, 역정규화 (x+1)*127.5 → [0,255]
 *
 * ArcFace 모델 (선택):
 *   assets/w600k_r50.onnx — 있으면 정확한 임베딩, 없으면 픽셀 기반 근사 임베딩 사용
 */
class OnnxFaceSwapper(private val context: Context) : FaceSwapper {

    private val INSWAPPER_MODEL = "inswapper_128.onnx"
    private val ARCFACE_MODEL   = "w600k_r50.onnx"
    private val SWAP_SIZE       = 128
    private val ARCFACE_SIZE    = 112

    private var ortEnv: OrtEnvironment? = null
    private var swapSession: OrtSession? = null
    private var arcSession:  OrtSession? = null

    /** 주입할 아이덴티티의 L2 정규화된 512-d 임베딩 */
    private var sourceEmbedding: FloatArray? = null

    override var isReady: Boolean = false
        private set

    override val hasTargetFace: Boolean
        get() = sourceEmbedding != null

    // ── 초기화 ─────────────────────────────────────────────────────────────────

    override fun prepare() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()

            // inswapper_128 로드 (대용량이므로 filesDir에 캐시)
            val swapPath = copyAssetToFiles(INSWAPPER_MODEL)
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                try { addNnapi() } catch (e: Exception) {
                    Log.w(TAG, "NNAPI 미지원 — CPU로 실행: ${e.message}")
                }
            }
            swapSession = ortEnv!!.createSession(swapPath, opts)
            isReady = true
            Log.d(TAG, "InSwapper 로드 완료 (입력: ${swapSession!!.inputNames})")
        } catch (e: Exception) {
            Log.e(TAG, "InSwapper 로드 실패: ${e.message}")
            isReady = false
            return
        }

        // ArcFace 로드 (선택)
        try {
            val arcPath = copyAssetToFiles(ARCFACE_MODEL)
            arcSession = ortEnv!!.createSession(arcPath, OrtSession.SessionOptions())
            Log.d(TAG, "ArcFace 로드 완료")
        } catch (e: Exception) {
            Log.w(TAG, "w600k_r50.onnx 없음 — 근사 임베딩 사용")
        }
    }

    // ── 타겟 얼굴 설정 ─────────────────────────────────────────────────────────

    override fun setTargetFace(faceBitmap: Bitmap) {
        val env = ortEnv ?: return
        val scaled = Bitmap.createScaledBitmap(faceBitmap, ARCFACE_SIZE, ARCFACE_SIZE, true)

        sourceEmbedding = when (val arc = arcSession) {
            null -> pseudoEmbedding(scaled)
            else -> arcFaceEmbedding(env, arc, scaled)
        }
        Log.d(TAG, "소스 임베딩 설정 완료 (norm=${l2norm(sourceEmbedding!!)})")
    }

    // ── 프레임 스왑 ────────────────────────────────────────────────────────────

    override fun swapFrame(frameBitmap: Bitmap): Bitmap {
        val embedding = sourceEmbedding ?: return frameBitmap
        val session   = swapSession     ?: return frameBitmap
        val env       = ortEnv          ?: return frameBitmap

        return runCatching {
            val w = frameBitmap.width
            val h = frameBitmap.height

            // 1. 얼굴 영역 추정 (전면 카메라 중앙 상단 기준)
            val faceRect = estimateFaceRect(w, h)

            // 2. 얼굴 크롭 → 128x128
            val crop = Bitmap.createBitmap(
                frameBitmap,
                faceRect.left.toInt(), faceRect.top.toInt(),
                faceRect.width().toInt(), faceRect.height().toInt()
            )
            val scaled = Bitmap.createScaledBitmap(crop, SWAP_SIZE, SWAP_SIZE, true)

            // 3. InSwapper 추론
            val targetTensor = bitmapToSwapTensor(env, scaled)
            val sourceTensor = embeddingToTensor(env, embedding)
            val inputs = mapOf("target" to targetTensor, "source" to sourceTensor)

            val swappedBitmap = session.run(inputs).use { result ->
                targetTensor.close()
                sourceTensor.close()
                val output = result.iterator().next().value.value
                @Suppress("UNCHECKED_CAST")
                swapOutputToBitmap(output as Array<Array<Array<FloatArray>>>)
            }

            // 4. 원본 프레임에 합성 결과 블렌딩
            blendFaceBack(frameBitmap, swappedBitmap, faceRect)
        }.onFailure { Log.e(TAG, "swapFrame 실패", it) }
         .getOrDefault(frameBitmap)
    }

    override fun release() {
        swapSession?.close()
        arcSession?.close()
        ortEnv?.close()
        swapSession  = null
        arcSession   = null
        ortEnv       = null
        sourceEmbedding = null
        isReady = false
    }

    // ── 임베딩 계산 ────────────────────────────────────────────────────────────

    /** ArcFace 모델로 정확한 512-d 임베딩 계산 */
    private fun arcFaceEmbedding(env: OrtEnvironment, session: OrtSession, bitmap: Bitmap): FloatArray {
        val tensor = bitmapToArcFaceTensor(env, bitmap)
        val name   = session.inputNames.iterator().next()
        return session.run(mapOf(name to tensor)).use { result ->
            tensor.close()
            @Suppress("UNCHECKED_CAST")
            val emb = (result.iterator().next().value.value as Array<FloatArray>)[0]
            emb.also { normalizeL2(it) }
        }
    }

    /**
     * ArcFace 없을 때 픽셀 기반 근사 임베딩.
     * 아이덴티티 정확도는 낮지만 파이프라인 동작 확인 가능.
     */
    private fun pseudoEmbedding(bitmap: Bitmap): FloatArray {
        val small  = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
        val pixels = IntArray(256).also { small.getPixels(it, 0, 16, 0, 0, 16, 16) }
        val embed  = FloatArray(512)
        for (i in pixels.indices) {
            embed[i * 2]     = ((pixels[i] shr 16 and 0xFF) / 127.5f) - 1f
            embed[i * 2 + 1] = ((pixels[i] shr 8  and 0xFF) / 127.5f) - 1f
        }
        return embed.also { normalizeL2(it) }
    }

    // ── 텐서 변환 ──────────────────────────────────────────────────────────────

    /**
     * Bitmap → [1, 3, 128, 128]
     * 정규화: (pixel/255 - 0.5) / 0.5 → [-1, 1]
     */
    private fun bitmapToSwapTensor(env: OrtEnvironment, bitmap: Bitmap): OnnxTensor {
        val pixels = IntArray(SWAP_SIZE * SWAP_SIZE)
            .also { bitmap.getPixels(it, 0, SWAP_SIZE, 0, 0, SWAP_SIZE, SWAP_SIZE) }
        val buf = FloatBuffer.allocate(3 * SWAP_SIZE * SWAP_SIZE)
        val r = FloatArray(SWAP_SIZE * SWAP_SIZE)
        val g = FloatArray(SWAP_SIZE * SWAP_SIZE)
        val b = FloatArray(SWAP_SIZE * SWAP_SIZE)
        for (i in pixels.indices) {
            val p = pixels[i]
            r[i] = ((p shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
            g[i] = ((p shr 8  and 0xFF) / 255f - 0.5f) / 0.5f
            b[i] = ((p        and 0xFF) / 255f - 0.5f) / 0.5f
        }
        buf.put(r); buf.put(g); buf.put(b); buf.rewind()
        return OnnxTensor.createTensor(
            env, buf, longArrayOf(1L, 3L, SWAP_SIZE.toLong(), SWAP_SIZE.toLong())
        )
    }

    /**
     * Bitmap → [1, 3, 112, 112] ArcFace 입력
     * 정규화: (pixel/255 - 0.5) / 0.5 → [-1, 1]
     */
    private fun bitmapToArcFaceTensor(env: OrtEnvironment, bitmap: Bitmap): OnnxTensor {
        val size   = ARCFACE_SIZE
        val pixels = IntArray(size * size)
            .also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
        val buf = FloatBuffer.allocate(3 * size * size)
        val r = FloatArray(size * size)
        val g = FloatArray(size * size)
        val b = FloatArray(size * size)
        for (i in pixels.indices) {
            val p = pixels[i]
            r[i] = ((p shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
            g[i] = ((p shr 8  and 0xFF) / 255f - 0.5f) / 0.5f
            b[i] = ((p        and 0xFF) / 255f - 0.5f) / 0.5f
        }
        buf.put(r); buf.put(g); buf.put(b); buf.rewind()
        return OnnxTensor.createTensor(
            env, buf, longArrayOf(1L, 3L, size.toLong(), size.toLong())
        )
    }

    /** 512-d float[] → [1, 512] OnnxTensor */
    private fun embeddingToTensor(env: OrtEnvironment, embedding: FloatArray): OnnxTensor =
        OnnxTensor.createTensor(
            env, FloatBuffer.wrap(embedding), longArrayOf(1L, 512L)
        )

    /**
     * [1, 3, 128, 128] output → Bitmap
     * 역정규화: (x + 1) * 127.5 → [0, 255]
     */
    private fun swapOutputToBitmap(chw: Array<Array<Array<FloatArray>>>): Bitmap {
        val bitmap = Bitmap.createBitmap(SWAP_SIZE, SWAP_SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SWAP_SIZE * SWAP_SIZE)
        val r = chw[0][0]; val g = chw[0][1]; val b = chw[0][2]
        for (y in 0 until SWAP_SIZE) {
            for (x in 0 until SWAP_SIZE) {
                val ri = ((r[y][x] + 1f) * 127.5f).toInt().coerceIn(0, 255)
                val gi = ((g[y][x] + 1f) * 127.5f).toInt().coerceIn(0, 255)
                val bi = ((b[y][x] + 1f) * 127.5f).toInt().coerceIn(0, 255)
                pixels[y * SWAP_SIZE + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        bitmap.setPixels(pixels, 0, SWAP_SIZE, 0, 0, SWAP_SIZE, SWAP_SIZE)
        return bitmap
    }

    // ── 얼굴 위치 추정 + 블렌딩 ───────────────────────────────────────────────

    /**
     * 전면 카메라 영상통화 기준 얼굴 위치 추정.
     * 실제 배포 시 MLKit FaceDetector로 교체 권장.
     */
    private fun estimateFaceRect(w: Int, h: Int): RectF {
        val size = minOf(w * 0.65f, h * 0.6f)
        val cx   = w * 0.5f
        val cy   = h * 0.38f   // 프레임 중앙보다 약간 위
        return RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2).also {
            it.intersect(0f, 0f, w.toFloat(), h.toFloat())
        }
    }

    /**
     * 합성된 128x128 얼굴을 원본 프레임의 얼굴 영역에 타원 마스크로 블렌딩
     */
    private fun blendFaceBack(frame: Bitmap, swapped: Bitmap, faceRect: RectF): Bitmap {
        val result = frame.copy(Bitmap.Config.ARGB_8888, true)
        val faceW  = faceRect.width().toInt().coerceAtLeast(1)
        val faceH  = faceRect.height().toInt().coerceAtLeast(1)

        val scaledSwap = Bitmap.createScaledBitmap(swapped, faceW, faceH, true)

        // 타원 마스크 생성
        val mask = Bitmap.createBitmap(faceW, faceH, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawOval(
            RectF(0f, 0f, faceW.toFloat(), faceH.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        )

        // 마스크 적용
        val masked = Bitmap.createBitmap(faceW, faceH, Bitmap.Config.ARGB_8888)
        Canvas(masked).apply {
            drawBitmap(scaledSwap, 0f, 0f, null)
            drawBitmap(mask, 0f, 0f, Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            })
        }

        Canvas(result).drawBitmap(masked, null, faceRect, null)
        return result
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    private fun normalizeL2(arr: FloatArray) {
        val norm = sqrt(arr.fold(0f) { acc, v -> acc + v * v }).coerceAtLeast(1e-6f)
        for (i in arr.indices) arr[i] /= norm
    }

    private fun l2norm(arr: FloatArray): Float =
        sqrt(arr.fold(0f) { acc, v -> acc + v * v })

    /** 대용량 모델 assets → filesDir 캐시 (중복 복사 방지) */
    private fun copyAssetToFiles(assetName: String): String {
        val out = File(context.filesDir, assetName)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            Log.d(TAG, "$assetName → filesDir 복사 완료 (${out.length() / 1_048_576}MB)")
        }
        return out.absolutePath
    }

    companion object {
        private const val TAG = "OnnxFaceSwapper"
    }
}