class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 네이티브 라이브러리 로드
    companion object {
        init {
            System.loadLibrary("shutter_android_ai")
        }
    }

    // C++ 함수 선언
    external fun loadModel(path: String): Boolean
    external fun runInference(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 모델 파일을 Asset에서 캐시 폴더로 복사 (C++ 접근용)
        val modelPath = getModelPath("face_deepfake_model.pt")

        // 2. 추론 테스트 시작 버튼 클릭
        binding.btnStartInference.setOnClickListener {
            binding.tvResult.text = "모델 로드 중..."

            if (loadModel(modelPath)) {
                val result = runInference()
                binding.tvResult.text = result
            } else {
                binding.tvResult.text = "모델 로드 실패"
            }
        }
    }

    // Asset에 있는 모델을 내장 메모리 경로로 변경해주는 유틸리티
    private fun getModelPath(modelName: String): String {
        val file = File(filesDir, modelName)
        if (!file.exists()) {
            assets.open(modelName).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }
}