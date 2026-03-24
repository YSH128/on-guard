package com.s14p21a404.shutter.presentation.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.s14p21a404.shutter.R


private const val PULSE_FRAME_COUNT = 40
private const val PULSE_INTERVAL_MS = 40L

// ── 피보호자 UI 모델 ──

private data class LocationGuardian(
    val id: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isSharing: Boolean = false,
    val profileImageUrl: String? = null,
)

// ── 색상 상수 ──

private val BgColor = Color(0xFFF2F5F9)
private val TitleColor = Color(0xFF2A2A2A)
private val SelectedBorderColor = Color(0xFF597BCE)
private val UnselectedAvatarBg = Color(0xFFD6DCE8)
private val SelectedAvatarBg = Color(0xFFF2F5F9)
private val UnselectedIconColor = Color(0xFF9CA3AF)
private val SelectedNameColor = Color(0xFF1A1A1A)
private val UnselectedNameColor = Color(0xFF6B7280)
private val EmptyIconColor = Color(0xFFC4C4C4)
private val EmptyTextColor = Color(0xFF9CA3AF)

/**
 * 위치 확인 화면 — 피보호자 목록 + Kakao Map 연동
 *
 * 보호자(현재 사용자)가 자신의 피보호자들의 실시간 위치를 확인합니다.
 */
@Composable
fun LocationScreen(
    locationViewModel: LocationViewModel = viewModel(),
) {
    val uiState by locationViewModel.uiState.collectAsState()

    // 화면 진입마다 피보호자 목록 새로고침
    LaunchedEffect(Unit) {
        locationViewModel.loadWards()
    }

    // WardWithLocation → LocationGuardian UI 모델 변환
    val wards = when (val s = uiState) {
        is LocationUiState.Success -> s.wards.map { ward ->
            LocationGuardian(
                id = ward.wardId.toString(),
                name = ward.name,
                latitude = ward.lat,
                longitude = ward.lng,
                isSharing = ward.isSharing,
                profileImageUrl = ward.profileImageUrl,
            )
        }
        else -> emptyList()
    }

    var selectedId by remember { mutableStateOf<String?>(null) }

    // 첫 데이터 수신 시 첫 번째 피보호자 자동 선택
    LaunchedEffect(wards) {
        if (selectedId == null && wards.isNotEmpty()) {
            selectedId = wards.first().id
        }
    }

    val selectedWard = wards.find { it.id == selectedId }
    val hasActiveLocation = selectedWard?.isSharing == true
            && selectedWard.latitude != null
            && selectedWard.longitude != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        HeaderTitle()

        when (uiState) {
            is LocationUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF6083EE))
                }
            }
            is LocationUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = (uiState as LocationUiState.Error).message,
                        color = Color(0xFF9CA3AF),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { locationViewModel.loadWards() }) {
                        Text("다시 시도", color = Color(0xFF6083EE))
                    }
                }
            }
            is LocationUiState.Success -> {
                GuardianSelector(
                    guardians = wards,
                    selectedId = selectedId,
                    onSelect = { selectedId = it }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasActiveLocation && selectedWard != null) {
                        KakaoMapWithMarker(
                            latitude = selectedWard.latitude!!,
                            longitude = selectedWard.longitude!!
                        )
                    } else {
                        EmptyLocationState()
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// 상단 타이틀
// ════════════════════════════════════════════════════════════

@Composable
private fun HeaderTitle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .padding(top = 36.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "위치 확인",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TitleColor
        )
    }
}

// ════════════════════════════════════════════════════════════
// 보호자 선택 가로 스크롤
// ════════════════════════════════════════════════════════════

@Composable
private fun GuardianSelector(
    guardians: List<LocationGuardian>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (guardians.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "등록된 보호자가 없습니다",
                fontSize = 14.sp,
                color = UnselectedNameColor
            )
        }
        return
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(guardians) { _, guardian ->
            val isSelected = selectedId == guardian.id

            GuardianAvatar(
                name = guardian.name,
                profileImageUrl = guardian.profileImageUrl,
                isSelected = isSelected,
                onClick = { onSelect(guardian.id) }
            )
        }
    }
}

@Composable
private fun GuardianAvatar(
    name: String,
    profileImageUrl: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // 아바타 원형
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) SelectedAvatarBg else UnselectedAvatarBg)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, SelectedBorderColor, RoundedCornerShape(16.dp))
                    } else {
                        Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(16.dp))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!profileImageUrl.isNullOrEmpty()) {
                SubcomposeAsyncImage(
                    model = profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_ep_user),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            colorFilter = ColorFilter.tint(if (isSelected) UnselectedIconColor else Color.White)
                        )
                    }
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_ep_user),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(if (isSelected) UnselectedIconColor else Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 이름
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) SelectedNameColor else UnselectedNameColor
        )
    }
}

// ════════════════════════════════════════════════════════════
// Kakao Map + 방패 마커 오버레이
// ════════════════════════════════════════════════════════════

@Composable
private fun KakaoMapWithMarker(
    latitude: Double,
    longitude: Double,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val markerBitmap = remember(context) { createShieldMarkerBitmap(context, density.density) }
    val pulseFrames = remember(context) { createPulseFrames(density.density, PULSE_FRAME_COUNT) }

    val mapView = remember { MapView(context) }
    var mapStarted by remember { mutableStateOf(false) }
    val animHandler = remember { Handler(Looper.getMainLooper()) }

    // 지도 초기화 후 참조를 보관해 위치 업데이트에 재사용
    var kakaoMapRef by remember { mutableStateOf<KakaoMap?>(null) }
    var pulseLabelRef by remember { mutableStateOf<Label?>(null) }
    var shieldLabelRef by remember { mutableStateOf<Label?>(null) }
    // 파장 스타일을 remember에 보관해 마커 재생성 시 재사용
    var pulseStylesRef by remember { mutableStateOf<List<LabelStyles?>>(emptyList()) }
    var shieldStyleRef by remember { mutableStateOf<LabelStyles?>(null) }
    var animStarted by remember { mutableStateOf(false) }

    // 기존 마커만 제거
    fun clearMarkers() {
        pulseLabelRef?.remove()
        shieldLabelRef?.remove()
        pulseLabelRef = null
        shieldLabelRef = null
    }

    // 마커를 (재)생성하는 함수 — 카메라는 건드리지 않음
    fun placeMarkers(kakaoMap: KakaoMap, lat: Double, lng: Double) {
        val targetLatLng = LatLng.from(lat, lng)
        val labelLayer = kakaoMap.labelManager?.layer ?: return

        // 파장 스타일이 아직 없으면 등록
        if (pulseStylesRef.isEmpty()) {
            pulseStylesRef = pulseFrames.map { frame ->
                kakaoMap.labelManager?.addLabelStyles(
                    LabelStyles.from(
                        LabelStyle.from(frame).setAnchorPoint(0.5f, 0.5f)
                    )
                )
            }
        }
        if (shieldStyleRef == null) {
            shieldStyleRef = kakaoMap.labelManager?.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(markerBitmap).setAnchorPoint(0.5f, 1.0f)
                )
            )
        }

        labelLayer.zOrder = 10001

        // 파장 라벨
        val firstPulseStyle = pulseStylesRef.firstOrNull()
        if (firstPulseStyle != null) {
            pulseLabelRef = labelLayer.addLabel(
                LabelOptions.from("pulse", targetLatLng)
                    .setStyles(firstPulseStyle)
                    .setRank(1L)
            )
        }

        // 파장 애니메이션 (한 번만 시작)
        if (!animStarted) {
            animStarted = true
            var frameIndex = 0
            val animRunnable = object : Runnable {
                override fun run() {
                    frameIndex = (frameIndex + 1) % PULSE_FRAME_COUNT
                    val nextStyle = pulseStylesRef.getOrNull(frameIndex)
                    if (nextStyle != null && pulseLabelRef != null) {
                        pulseLabelRef!!.changeStyles(nextStyle)
                    }
                    animHandler.postDelayed(this, PULSE_INTERVAL_MS)
                }
            }
            animHandler.postDelayed(animRunnable, PULSE_INTERVAL_MS)
        }

        // 방패 마커
        val sStyle = shieldStyleRef
        if (sStyle != null) {
            shieldLabelRef = labelLayer.addLabel(
                LabelOptions.from(targetLatLng)
                    .setStyles(sStyle)
                    .setRank(Long.MAX_VALUE)
            )
        }
    }

    // ── 피보호자 전환 감지: 즉시 전환 (디졸브 없음) ──
    // 초기 컴포지션을 구분하기 위한 플래그
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(wardId) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }

        val map = kakaoMapRef ?: return@LaunchedEffect

        clearMarkers()

        if (latitude != null && longitude != null) {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(latitude, longitude), 16))
            placeMarkers(map, latitude, longitude)
        }
    }

    // ── 같은 피보호자의 실시간 위치 업데이트: 부드럽게 이동 ──
    LaunchedEffect(latitude, longitude) {
        val map = kakaoMapRef ?: return@LaunchedEffect
        val newLatLng = LatLng.from(latitude, longitude)
        map.moveCamera(CameraUpdateFactory.newCenterPosition(newLatLng, 16))
        pulseLabelRef?.moveTo(newLatLng)
        shieldLabelRef?.moveTo(newLatLng)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (mapStarted) mapView.resume()
                Lifecycle.Event.ON_PAUSE  -> if (mapStarted) mapView.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            animHandler.removeCallbacksAndMessages(null)
            if (mapStarted) mapView.finish()
        }
    }

    // 지도 start()를 실행하는 공통 함수
    fun MapView.startMapIfNeeded() {
        if (mapStarted) return
        mapStarted = true
        start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() { Log.d("KakaoMap", "지도 파괴됨") }
                override fun onMapError(error: Exception?) {
                    Log.e("KakaoMap", "지도 에러: ${error?.message}", error)
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(kakaoMap: KakaoMap) {
                    Log.d("KakaoMap", "지도 준비 완료")
                    kakaoMapRef = kakaoMap
                    if (latitude != null && longitude != null) {
                        kakaoMap.moveCamera(
                            CameraUpdateFactory.newCenterPosition(
                                LatLng.from(latitude, longitude), 16
                            )
                        )
                        placeMarkers(kakaoMap, latitude, longitude)
                    }
                }
            }
        )
        resume()
    }


    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            startMapIfNeeded()
                        }
                        override fun onViewDetachedFromWindow(v: android.view.View) {
                            Log.d("KakaoMap", "MapView detached")
                        }
                    })
                    if (isAttachedToWindow) {
                        startMapIfNeeded()
                    }
                }
            },
            update = { view ->
                view.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            },
            modifier = Modifier.fillMaxSize()
        )

    }
}

// ════════════════════════════════════════════════════════════
// 지도 로딩 스켈레톤 (shimmer)
// ════════════════════════════════════════════════════════════

/**
 * 파장 펄스 애니메이션 프레임 배열 생성
 * 원이 작은 상태에서 커지면서 투명해지다 사라지고 반복
 */
private fun createPulseFrames(density: Float, frameCount: Int): List<Bitmap> {
    val maxSize = (160 * density).toInt()
    val minScale = 0.3f
    val maxScale = 1.0f
    val startAlpha = 0x99  // ~60%

    return (0 until frameCount).map { i ->
        val t = i.toFloat() / frameCount  // 0..1 진행도

        val scale = minScale + (maxScale - minScale) * t
        val alpha = (startAlpha * (1f - t)).toInt().coerceIn(0, 255)

        val size = (maxSize * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(maxSize, maxSize, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val cx = maxSize / 2f
        val cy = maxSize / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (alpha shl 24) or 0xB5DFFF
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, size * 0.5f, paint)

        bitmap
    }
}

/**
 * 방패 모양 마커 Bitmap 생성 (Android Canvas 기반)
 * LabelLayer에 좌표 고정으로 사용
 */
private fun createShieldMarkerBitmap(context: android.content.Context, density: Float): Bitmap {
    val w = (64 * density).toInt()
    val h = (96 * density).toInt()    // 방패 + 간격 + 도트 포함 높이
    val shieldH = (76 * density)
    val dotOuterRadius = (8 * density)
    val dotInnerRadius = (2.5f * density)
    val dotStrokeWidth = (5f * density)

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    // ── 방패 그리기 ──
    val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, shieldH,
            0xFF5B7FD6.toInt(), 0xFF4A6091.toInt(),
            Shader.TileMode.CLAMP
        )
    }

    val shieldPath = android.graphics.Path().apply {
        moveTo(w * 0.5f, 0f)
        lineTo(w.toFloat(), shieldH * 0.167f)
        lineTo(w.toFloat(), shieldH * 0.5f)
        cubicTo(w.toFloat(), shieldH * 0.75f, w * 0.75f, shieldH * 0.917f, w * 0.5f, shieldH)
        cubicTo(w * 0.25f, shieldH * 0.917f, 0f, shieldH * 0.75f, 0f, shieldH * 0.5f)
        lineTo(0f, shieldH * 0.167f)
        close()
    }
    canvas.drawPath(shieldPath, shieldPaint)

    // ── 방패 안 흰색 원 ──
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }
    canvas.drawCircle(w * 0.5f, shieldH * 0.417f, w * 0.275f, circlePaint)

    // ── 방패 안 사용자 아이콘 (파란색) ──
    val iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ep_user)
    if (iconDrawable != null) {
        val wrapped = DrawableCompat.wrap(iconDrawable.mutate())
        DrawableCompat.setTint(wrapped, 0xFF5B7FD6.toInt())
        val iconSize = (24 * density).toInt()
        val iconLeft = (w - iconSize) / 2
        val iconTop = (shieldH * 0.417f - iconSize / 2).toInt()
        wrapped.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        wrapped.draw(canvas)
    }

    // ── 하단 포인터 도트 (흰 원 + 파란 테두리) ──
    val dotCenterY = shieldH + (4 * density) + dotOuterRadius
    // 파란 테두리
    val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5B7FD6.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dotStrokeWidth
    }
    canvas.drawCircle(w * 0.5f, dotCenterY, dotOuterRadius, dotStrokePaint)
    // 흰색 채우기
    val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(w * 0.5f, dotCenterY, dotInnerRadius, dotFillPaint)

    return bitmap
}

// ════════════════════════════════════════════════════════════
// 빈 상태 (위치 미활성)
// ════════════════════════════════════════════════════════════

@Composable
private fun EmptyLocationState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // 지도 꺼진 아이콘 (Canvas로 그리기)
        Canvas(
            modifier = Modifier.size(96.dp)
        ) {
            drawMapOffIcon(this)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "위험 감지 이후 30분동안 제공됩니다.",
            fontSize = 16.sp,
            color = EmptyTextColor
        )
    }
}

/**
 * 지도 꺼짐 아이콘을 Canvas로 그리기
 * (3개의 접힌 지도 패널 + 대각선 슬래시)
 */
private fun drawMapOffIcon(drawScope: DrawScope) {
    with(drawScope) {
        val w = size.width
        val h = size.height
        val strokeW = 2.5.dp.toPx()

        // 왼쪽 패널
        val leftPanel = Path().apply {
            moveTo(w * 0.208f, h * 0.25f)
            lineTo(w * 0.375f, h * 0.167f)
            lineTo(w * 0.375f, h * 0.667f)
            lineTo(w * 0.208f, h * 0.75f)
            close()
        }
        drawPath(
            path = leftPanel,
            color = EmptyIconColor,
            style = Stroke(width = strokeW)
        )

        // 중앙 패널
        val centerPanel = Path().apply {
            moveTo(w * 0.375f, h * 0.167f)
            lineTo(w * 0.625f, h * 0.25f)
            lineTo(w * 0.625f, h * 0.75f)
            lineTo(w * 0.375f, h * 0.667f)
            close()
        }
        drawPath(
            path = centerPanel,
            color = EmptyIconColor,
            style = Stroke(width = strokeW)
        )

        // 오른쪽 패널
        val rightPanel = Path().apply {
            moveTo(w * 0.625f, h * 0.25f)
            lineTo(w * 0.792f, h * 0.167f)
            lineTo(w * 0.792f, h * 0.667f)
            lineTo(w * 0.625f, h * 0.75f)
            close()
        }
        drawPath(
            path = rightPanel,
            color = EmptyIconColor,
            style = Stroke(width = strokeW)
        )

        // 대각선 슬래시
        drawLine(
            color = EmptyIconColor,
            start = Offset(w * 0.167f, h * 0.833f),
            end = Offset(w * 0.833f, h * 0.167f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}
