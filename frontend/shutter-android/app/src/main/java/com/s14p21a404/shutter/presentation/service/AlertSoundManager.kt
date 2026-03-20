package com.s14p21a404.shutter.presentation.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

/**
 * 딥페이크 감지 경고음 재생 매니저
 *
 * - 딥페이크 감지 시 경고음을 **반복 재생**한다.
 * - 영상통화 종료(또는 딥페이크 해제) 시 재생을 **즉시 중지**한다.
 * - 재생 전 시스템 미디어 볼륨을 **강제로 특정 비율(기본 80 %)** 이상으로 올려,
 *   사용자가 소리를 작게 해 둬도 경고음이 충분히 들리도록 한다.
 * - 경고음 종료 시 원래 볼륨으로 복원한다.
 *
 * 리소스: res/raw/deepfake_alert.wav (또는 .mp3/.ogg)
 */
class AlertSoundManager(private val context: Context) {

    companion object {
        private const val TAG = "AlertSoundManager"

        /**
         * 시스템 최대 볼륨 대비 최소 보장 비율 (0.0 ~ 1.0).
         * 현재 볼륨이 이 비율보다 낮으면 강제로 올린다.
         */
        private const val MIN_VOLUME_RATIO = 0.10f
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var mediaPlayer: MediaPlayer? = null

    /** 경고음 재생 전 사용자의 원래 볼륨 (복원용) */
    private var savedVolume: Int = -1

    /** 현재 경고음이 재생 중인지 */
    @Volatile
    var isPlaying: Boolean = false
        private set

    // ── Public API ──

    /**
     * 경고음 반복 재생을 시작한다.
     * 이미 재생 중이면 무시한다.
     */
    fun startAlert() {
        if (isPlaying) {
            Log.d(TAG, "이미 경고음 재생 중 — 무시")
            return
        }

        Log.d(TAG, "🔊 경고음 재생 시작")

        // 1) 볼륨 강제 확보
        ensureMinVolume()

        // 2) MediaPlayer 생성 & 반복 재생
        try {
            val resId = context.resources.getIdentifier("deepfake_alert", "raw", context.packageName)
            if (resId == 0) {
                Log.e(TAG, "⚠️ res/raw/deepfake_alert 리소스를 찾을 수 없음")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)          // 알람 스트림 → DND 모드에서도 울림
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                // raw 리소스를 URI 로 세팅
                val uri = android.net.Uri.parse(
                    "android.resource://${context.packageName}/$resId"
                )
                setDataSource(context, uri)
                isLooping = true        // 무한 반복
                prepare()
                start()
            }

            isPlaying = true
            Log.d(TAG, "🔊 경고음 재생 시작 완료")
        } catch (e: Exception) {
            Log.e(TAG, "경고음 재생 실패", e)
            releasePlayer()
        }
    }

    /**
     * 경고음 재생을 즉시 중지하고 리소스를 해제한다.
     * 볼륨도 원래 값으로 복원한다.
     */
    fun stopAlert() {
        if (!isPlaying && mediaPlayer == null) return

        Log.d(TAG, "🔇 경고음 중지")
        releasePlayer()
        restoreVolume()
        isPlaying = false
    }

    // ── 내부 구현 ──

    /**
     * 현재 STREAM_ALARM 볼륨이 [MIN_VOLUME_RATIO] 미만이면 강제로 올린다.
     */
    private fun ensureMinVolume() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val targetVolume = (maxVolume * MIN_VOLUME_RATIO).toInt().coerceAtMost(maxVolume)

        // 현재 볼륨 백업
        savedVolume = currentVolume

        if (currentVolume < targetVolume) {
            Log.d(TAG, "볼륨 강제 조정: $currentVolume → $targetVolume (max=$maxVolume)")
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                targetVolume,
                0   // 플래그 0 = UI 표시 없이 조용히 변경
            )
        } else {
            Log.d(TAG, "현재 볼륨 충분: $currentVolume / $maxVolume")
        }
    }

    /**
     * 볼륨을 경고음 재생 전 상태로 복원한다.
     */
    private fun restoreVolume() {
        if (savedVolume < 0) return
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        if (currentVolume != savedVolume) {
            Log.d(TAG, "볼륨 복원: $currentVolume → $savedVolume")
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
        }
        savedVolume = -1
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer 해제 오류", e)
        }
        mediaPlayer = null
    }
}

