package com.s14p21a404.shutter.presentation.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GMS API가 분석한 STT 키워드 태그를 VideoCallScreen → FinancialBlockOverlayManager로 전달하는 브릿지.
 *
 * 위험 태그(금융거래·개인정보요구·특정장소유인)가 한 번이라도 감지되면
 * 이후 #일상대화가 와도 위험 태그를 유지한다.
 */
object SttTagBridge {

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    /** 한 번 감지된 위험 태그 누적 세트 (통화 종료 전까지 유지) */
    private val stickyDangerousTags = mutableSetOf<String>()

    /** GMS 분석 결과 raw 문자열을 파싱하여 저장한다. */
    fun update(rawGmsResult: String) {
        val tokens = rawGmsResult.trim()
            .split("\\s+".toRegex())
            .filter { it.startsWith("#") }

        // 위험 태그가 있으면 누적
        tokens.filter { it in DANGEROUS_TAGS }.forEach { stickyDangerousTags.add(it) }

        val displayTags = if (stickyDangerousTags.isNotEmpty()) {
            // 위험 태그가 한 번이라도 나왔으면 위험 태그만 표시
            stickyDangerousTags.mapNotNull { GMS_TAG_DISPLAY[it] }
        } else {
            // 아직 위험 태그 없으면 현재 결과 그대로
            tokens.map { GMS_TAG_DISPLAY[it] ?: it }
        }

        if (displayTags.isNotEmpty()) {
            _tags.value = displayTags
        }
    }

    fun clear() {
        stickyDangerousTags.clear()
        _tags.value = emptyList()
    }

    private val DANGEROUS_TAGS = setOf("#금융거래", "#특정장소유인", "#개인정보요구")

    /** GMS 카테고리 → 화면 표시 텍스트 */
    private val GMS_TAG_DISPLAY = mapOf(
        "#금융거래"     to "# 금융 거래",
        "#특정장소유인" to "# 특정 장소 유인",
        "#개인정보요구" to "# 개인정보 요구",
        "#일상대화"     to "# 일상 대화",
    )
}
