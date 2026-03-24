package com.s14p21a404.shutter.presentation.criminal

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class FaceSetupUiState(
    val photoUri: Uri? = null,
    val isPreparing: Boolean = false,
    val isReady: Boolean = false,
    val error: String? = null,
)

class FaceSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FaceSetupUiState())
    val uiState: StateFlow<FaceSetupUiState> = _uiState.asStateFlow()

    /** 서버로 전송할 소스 얼굴 JPEG 바이트 */
    var sourceFaceJpeg: ByteArray? = null
        private set

    fun onPhotoSelected(uri: Uri) {
        _uiState.update { it.copy(photoUri = uri, isReady = false, error = null) }
        loadFace(uri)
    }

    private fun loadFace(uri: Uri) {
        _uiState.update { it.copy(isPreparing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bitmap = loadBitmapWithExif(uri)

                // 최대 512px로 축소 후 JPEG 직렬화 (서버 전송용)
                val scaled = scaleBitmap(bitmap, 512)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                sourceFaceJpeg = out.toByteArray()
                Log.d(TAG, "소스 얼굴 JPEG 준비 완료 (${sourceFaceJpeg!!.size / 1024} KB)")
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isPreparing = false, isReady = true) }
                }
            }.onFailure { e ->
                Log.e(TAG, "얼굴 로드 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isPreparing = false, error = e.message) }
                }
            }
        }
    }

    private fun loadBitmapWithExif(uri: Uri): Bitmap {
        val context = getApplication<Application>()
        val bitmap = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it) }
            ?: error("이미지를 읽을 수 없습니다.")

        // FileProvider URI(카메라 촬영) → 픽셀이 이미 올바른 방향, EXIF 보정 불필요
        if (uri.authority?.contains(context.packageName) == true) return bitmap

        // 갤러리 등 외부 이미지 → EXIF 회전 보정
        val degrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val scale = maxSize.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "FaceSetupViewModel"
    }
}