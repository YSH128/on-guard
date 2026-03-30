package com.s14p21a404.shutter.presentation.login

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.repeatCount
import com.s14p21a404.shutter.R

// ── 색상 상수 ──
private val BackgroundColor = Color(0xFFF7F7F7)
private val KakaoYellow = Color(0xFFFEE500)
private val KakaoTextColor = Color(0xFF191919)
private val SocialLabelColor = Color(0xFFAAAAAA)
private val TitleColor = Color(0xFF1A1A2E)

@Composable
fun LoginScreen(
    onKakaoLogin: () -> Unit,
    loginError: String? = null,
    onDismissError: () -> Unit = {}
) {
    // ── 에러 다이얼로그 ──
    if (loginError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(text = "로그인 실패") },
            text = { Text(text = loginError) },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("확인")
                }
            }
        )
    }

    // ── GIF ImageLoader (한 번만 재생) ──
    val context = LocalContext.current
    val gifImageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── GIF 로고 (한 번만 재생) ──
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(R.drawable.logo_gif)
                    .repeatCount(0)
                    .build(),
                contentDescription = "Shutter 로고",
                imageLoader = gifImageLoader,
                modifier = Modifier.size(160.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 서비스명 타이틀 ──
            Text(
                text = "OnGuard",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TitleColor,
                textAlign = TextAlign.Center
            )
        }

        // ── 하단: 소셜 로그인 영역 ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "소셜 로그인",
                fontSize = 13.sp,
                color = SocialLabelColor,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ── 카카오 로그인 버튼 ──
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(KakaoYellow)
                    .clickable { onKakaoLogin() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 카카오 말풍선 아이콘
                    Icon(
                        painter = painterResource(id = R.drawable.ic_kakao_chat),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = KakaoTextColor
                    )
                    Text(
                        text = "카카오 로그인",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = KakaoTextColor,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    // 오른쪽 밸런스용 빈 공간
                    Spacer(modifier = Modifier.width(18.dp))
                }
            }
        }
    }
}
