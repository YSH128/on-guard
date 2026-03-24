package com.s14p21a404.shutter.presentation.criminal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

private val DarkBg     = Color(0xFF1A1A2E)
private val AccentRed  = Color(0xFFE53935)
private val CardBg     = Color(0xFF16213E)
private val TextWhite  = Color(0xFFE0E0E0)
private val TextGray   = Color(0xFF9E9E9E)
private val SuccessGreen = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceSetupScreen(
    onBack: () -> Unit,
    viewModel: FaceSetupViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraUri?.let { viewModel.onPhotoSelected(it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onPhotoSelected(it) }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("타겟 얼굴 설정", color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
            )
        },
        containerColor = DarkBg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 사진 미리보기
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(
                        width = 1.dp,
                        color = when {
                            uiState.isReady -> SuccessGreen
                            uiState.photoUri != null -> AccentRed
                            else -> Color(0xFF3A3A5C)
                        },
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val uri = uiState.photoUri
                if (uri != null) {
                    val bitmap = remember(uri) {
                        loadBitmapRotated(context, uri)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "선택된 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ImageSearch, contentDescription = null, tint = TextGray, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("타겟 얼굴 사진을 선택하세요", color = TextGray, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 사진 선택 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            val uri = createCameraUri(context)
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3A5C)),
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("카메라 촬영")
                }

                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3A5C)),
                ) {
                    Icon(Icons.Outlined.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("갤러리 선택")
                }
            }

            Spacer(Modifier.height(16.dp))

            // 상태 표시
            when {
                uiState.isPreparing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBg)
                            .padding(16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentRed, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("얼굴 인식 중...", color = TextGray, fontSize = 14.sp)
                    }
                }
                uiState.isReady -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1B3A2A))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("설정 완료", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("영상통화 시 자동으로 얼굴이 합성됩니다.", color = TextGray, fontSize = 12.sp)
                        }
                    }
                }
                uiState.error != null -> {
                    Text(uiState.error!!, color = AccentRed, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun createCameraUri(context: android.content.Context): Uri {
    val file = File(context.cacheDir, "face_capture.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

private fun loadBitmapRotated(context: Context, uri: Uri): Bitmap? {
    val bitmap = context.contentResolver.openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it) } ?: return null

    val degrees = try {
        // 카메라(FileProvider)는 실제 파일 경로로 EXIF 읽기, 갤러리는 스트림으로
        val exif = if (uri.authority?.contains(context.packageName) == true) {
            val file = java.io.File(context.cacheDir, "face_capture.jpg")
            ExifInterface(file.absolutePath)
        } else {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        }
        when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) { 0f }

    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}