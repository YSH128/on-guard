package com.s14p21a404.shutter.domain.model

/**
 * 딥페이크 감지 결과
 *
 * AI 서버 응답 JSON 형식:
 * ```json
 * {
 *     "detected": 1,
 *     "roi": [0.3, 0.22, 0.45, 0.567]
 * }
 * ```
 *
 * - detected: 딥페이크/딥보이스 검출 여부 (0 or 1)
 * - roi: 딥페이크 의심 영역의 상대 좌표 [minX, minY, maxX, maxY] (0.0~1.0)
 */
data class DeepfakeDetectionResult(
    val detected: Boolean,

    val fakeProb: Float = 0f,
    val roi: FloatArray? = null,       // [minX, minY, maxX, maxY] 상대좌표, 미검출 시 null
    val timestamp: Long = System.currentTimeMillis(),
    val rawResultJson: String? = null  // NDK로부터 받은 전체 JSON (텔레메트리용 추가 필드 포함)
) {
    /** roi 편의 접근 */
    val roiMinX: Float get() = roi?.getOrNull(0) ?: 0f
    val roiMinY: Float get() = roi?.getOrNull(1) ?: 0f
    val roiMaxX: Float get() = roi?.getOrNull(2) ?: 0f
    val roiMaxY: Float get() = roi?.getOrNull(3) ?: 0f
    val hasRoi: Boolean get() = roi != null && roi.size == 4

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeepfakeDetectionResult) return false
        return detected == other.detected
                && roi.contentEqualsNullable(other.roi)
                && timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = detected.hashCode()
        result = 31 * result + (roi?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }

    override fun toString(): String {
        return "DeepfakeDetectionResult(detected=$detected, roi=${roi?.contentToString()}, timestamp=$timestamp)"
    }
}

private fun FloatArray?.contentEqualsNullable(other: FloatArray?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return false
    return this.contentEquals(other)
}

