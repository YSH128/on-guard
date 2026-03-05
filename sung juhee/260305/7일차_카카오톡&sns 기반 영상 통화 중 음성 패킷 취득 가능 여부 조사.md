## 카카오톡&sns 기반 영상 통화 중 음성 패킷 취득 가능 여부 조사

- 카카오톡이나 SNS의 음성 패킷을 네트워크 단에서 직접 캡처하여 분석하는 것은 종단간 암호화(E2EE)로 인해 불가능에 가깝습니다. 대신 운영체제(OS) 수준에서 오디오 루프백(Loopback)을 통해 복호화된 음성 데이터를 수집하는 방식을 권장합니다.
- 기기 내에서 오디오가 처리되는 엔드포인트(Endpoint) 단에서 캡처하는 방향으로 접근해야 합니다.
- AudioPlaybackCapture API: Android 10부터 시스템 오디오를 캡처하는 API가 추가되었지만, 캡처 대상 앱(카카오톡 등)의 매니페스트 설정(allowAudioPlaybackCapture="true")이 필요합니다
- 메신저 앱에 allowAudioPlaybackCapture="true"가 설정되어 있더라도, 안드로이드 시스템은 영상/음성 통화 오디오 스트림(USAGE_VOICE_COMMUNICATION)의 캡처를 보안상의 이유로 강제로 차단합니다
- Android 10부터 도입된 AudioPlaybackCapture API를 사용하면 해당 설정이 true로 되어 있는 앱의 소리(예: 앱 내에서 재생되는 유튜브 영상 소리 등)를 녹음할 수 있는 것은 사실입니다. 하지만 이는 USAGE_MEDIA, USAGE_GAME, USAGE_UNKNOWN 속성을 가진 오디오에만 적용됩니다.
