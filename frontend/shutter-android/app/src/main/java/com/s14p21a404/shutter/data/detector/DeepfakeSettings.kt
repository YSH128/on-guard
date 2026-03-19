package com.s14p21a404.shutter.data.detector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 딥페이크 탐지 관련 동작 옵션을 전역으로 관리하는 설정 객체
 * 추후 ViewModel과 연결하여 사용자 UI 토글 버튼으로 제어될 수 있습니다.
 */
object DeepfakeSettings {

    /**
     * VRAM에 모델을 상시 적재할지 여부.
     *
     * - true: 앱 시작(캡처 권한 동의) 시점에 AI 모델을 완전히 VRAM에 올려두고 계속 대기. 
     *         초기 로딩 시 약간의 메모리 점유가 있으나, 실제 영상통화 감지 시 0ms 딜레이 보장.
     *
     * - false: 앱 시작 시점에는 디스크에 바이너리 캐시(.bin)만 생성해두고 모델은 VRAM에서 즉시 해제.
     *          영상통화 감지 시 다시 캐시에서 읽어와 VRAM에 적재. (QNN GPU 백엔드의 경우 약 10초 이상의 큰 지연 발생 가능)
     *
     * 현재 QNN 프레임워크의 특성상 true를 강권합니다.
     */
    private val _keepModelInVram = MutableStateFlow(true)
    val keepModelInVram: StateFlow<Boolean> get() = _keepModelInVram

    fun setKeepModelInVram(enabled: Boolean) {
        _keepModelInVram.value = enabled
    }
}
