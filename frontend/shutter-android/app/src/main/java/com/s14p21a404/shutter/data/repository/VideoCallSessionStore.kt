package com.s14p21a404.shutter.data.repository

/**
 * 영상통화 세션 데이터를 컴포넌트 간에 공유하는 싱글톤 저장소.
 * 토큰 문자열은 URL에 포함하기에 너무 길어 여기서 전달한다.
 */
object VideoCallSessionStore {
    /** Agora joinChannel에 사용할 토큰 (발신자: POST /calls 응답, 수신자: POST /accept 응답) */
    var agoraToken: String? = null

    /** 현재 통화 채널명 */
    var channelName: String? = null

    fun clear() {
        agoraToken = null
        channelName = null
    }
}