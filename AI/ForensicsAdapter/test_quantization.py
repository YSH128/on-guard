"""
ONNX 양자화 전후 성능 비교 스크립트
- 원본 ONNX 모델과 양자화 모델의 정확도/속도 비교
- 양자화 효과 검증용
"""

import numpy as np
import cv2
import time
import glob
from pathlib import Path

# ONNX Runtime 인퍼런스 엔진
try:
    import onnxruntime as ort
except ImportError:
    print("❌ onnxruntime이 설치되어 있지 않습니다.")
    print("설치: pip install onnxruntime")
    exit(1)


class QuantizationTester:
    def __init__(self, original_onnx, quantized_onnx, test_image_dir="valid_data"):
        """
        Args:
            original_onnx: 원본 ONNX 모델 경로
            quantized_onnx: 양자화된 ONNX 모델 경로
            test_image_dir: 테스트 이미지 디렉토리
        """
        self.original_onnx = original_onnx
        self.quantized_onnx = quantized_onnx
        self.test_image_dir = test_image_dir
        
        # 세션 생성
        print("모델 로드 중...")
        try:
            self.sess_original = ort.InferenceSession(
                original_onnx,
                providers=['CPUExecutionProvider']
            )
            print(f"✓ 원본 모델 로드: {original_onnx}")
        except FileNotFoundError:
            print(f"❌ 파일을 찾을 수 없습니다: {original_onnx}")
            return
        
        try:
            self.sess_quantized = ort.InferenceSession(
                quantized_onnx,
                providers=['CPUExecutionProvider']
            )
            print(f"✓ 양자화 모델 로드: {quantized_onnx}")
        except FileNotFoundError:
            print(f"⚠️  양자화 모델을 찾을 수 없습니다: {quantized_onnx}")
            self.sess_quantized = None
        
        # 입력/출력 정보
        self.input_name = self.sess_original.get_inputs()[0].name
        self.output_names = [o.name for o in self.sess_original.get_outputs()]
        
        print(f"입력: {self.input_name}")
        print(f"출력: {self.output_names}\n")
    
    def load_test_images(self, max_images=50):
        """테스트 이미지 로드"""
        image_files = glob.glob(f"{self.test_image_dir}/**/*.jpg", recursive=True)
        image_files += glob.glob(f"{self.test_image_dir}/**/*.png", recursive=True)
        
        images = []
        for img_path in image_files[:max_images]:
            try:
                img = cv2.imread(img_path)
                if img is None:
                    continue
                img = cv2.resize(img, (256, 256))
                img = img.astype(np.float32) / 255.0
                img = np.transpose(img, (2, 0, 1))  # HWC -> CHW
                images.append(np.expand_dims(img, 0))  # 배치 차원
            except Exception as e:
                print(f"⚠️  이미지 로드 실패: {img_path}")
                continue
        
        return images
    
    def run_inference(self, images, sess):
        """모델 인퍼런스 및 시간 측정"""
        logits_list = []
        cam_list = []
        
        # Warmup
        for _ in range(2):
            sess.run(self.output_names, {self.input_name: images[0]})
        
        # 실제 추론
        start_time = time.time()
        for img in images:
            outputs = sess.run(self.output_names, {self.input_name: img})
            logits_list.append(outputs[0])
            if len(outputs) > 1:
                cam_list.append(outputs[1])
        
        elapsed = time.time() - start_time
        avg_time_per_image = elapsed / len(images) * 1000  # ms
        
        return logits_list, cam_list, avg_time_per_image
    
    def calculate_cosine_similarity(self, outputs1, outputs2):
        """두 출력의 코사인 유사도 계산 (출력 차이 측정)"""
        similarities = []
        for o1, o2 in zip(outputs1, outputs2):
            o1_flat = o1.flatten()
            o2_flat = o2.flatten()
            
            # L2 정규화
            o1_norm = o1_flat / (np.linalg.norm(o1_flat) + 1e-8)
            o2_norm = o2_flat / (np.linalg.norm(o2_flat) + 1e-8)
            
            # 코사인 유사도
            cos_sim = np.dot(o1_norm, o2_norm)
            similarities.append(cos_sim)
        
        return np.mean(similarities), np.std(similarities)
    
    def test(self, num_test_images=50):
        """양자화 성능 테스트"""
        print("="*70)
        print("양자화 성능 테스트")
        print("="*70)
        
        # 테스트 이미지 로드
        print(f"\n📁 테스트 이미지 로드 중... ({self.test_image_dir})")
        images = self.load_test_images(max_images=num_test_images)
        
        if not images:
            print(f"❌ 테스트 이미지를 찾을 수 없습니다: {self.test_image_dir}")
            return
        
        print(f"✓ {len(images)}개 이미지 로드 완료\n")
        
        # 원본 모델 추론
        print("원본 모델 추론 중...")
        logits_orig, cam_orig, time_orig = self.run_inference(images, self.sess_original)
        
        # 양자화 모델 추론
        if self.sess_quantized:
            print("양자화 모델 추론 중...")
            logits_quant, cam_quant, time_quant = self.run_inference(images, self.sess_quantized)
            
            # 결과 비교
            print("\n" + "="*70)
            print("📊 결과 비교")
            print("="*70)
            
            # 속도 비교
            print(f"\n⏱️  추론 속도 (평균):")
            print(f"   원본:     {time_orig:.3f} ms/image")
            print(f"   양자화:   {time_quant:.3f} ms/image")
            print(f"   속도 향상: {(time_orig/time_quant - 1)*100:.1f}%")
            
            # 출력값 비교
            print(f"\n🎯 출력값 유사도 (코사인):")
            
            # Logits
            sim_logits, std_logits = self.calculate_cosine_similarity(logits_orig, logits_quant)
            print(f"   Logits: {sim_logits:.4f} ± {std_logits:.4f}")
            if sim_logits > 0.99:
                print(f"   ✓ 매우 높은 유사도 - 양자화 권장")
            elif sim_logits > 0.95:
                print(f"   ✓ 좋은 유사도 - 양자화 가능")
            else:
                print(f"   ⚠️  유사도가 낮음 - 다른 양자화 방식 고려")
            
            # CAM
            if cam_orig and cam_quant:
                sim_cam, std_cam = self.calculate_cosine_similarity(cam_orig, cam_quant)
                print(f"   CAM:    {sim_cam:.4f} ± {std_cam:.4f}")
            
            # 파일 크기
            print(f"\n💾 파일 크기:")
            orig_size = Path(self.original_onnx).stat().st_size / (1024*1024)
            quant_size = Path(self.quantized_onnx).stat().st_size / (1024*1024)
            reduction = (1 - quant_size/orig_size) * 100
            print(f"   원본:      {orig_size:.2f} MB")
            print(f"   양자화:    {quant_size:.2f} MB")
            print(f"   감소율:    {reduction:.1f}%")
            
            # 결론
            print(f"\n" + "="*70)
            if sim_logits > 0.99:
                print("✓ 추천: 이 양자화 모델을 프로덕션에 사용해도 됩니다!")
                print("  - 파일 크기는 크게 줄었습니다")
                print("  - 출력 정확도는 거의 손실이 없습니다")
            elif sim_logits > 0.95:
                print("⚠️  주의: 양자화 모델을 사용하기 전에 추가 검증이 필요합니다")
                print("  - Static quantization을 시도해보세요")
            else:
                print("❌ 이 양자화 설정은 권장되지 않습니다")
                print("  - 다른 양자화 방식을 시도하세요")
            print("="*70)
        else:
            print("⚠️  양자화 모델을 로드할 수 없어 비교를 건너뜁니다.")
            print(f"원본 모델 추론 시간: {time_orig:.3f} ms/image")


if __name__ == "__main__":
    # 테스트 실행
    tester = QuantizationTester(
        original_onnx="ds_model.onnx",
        quantized_onnx="ds_model_quantized.onnx",
        test_image_dir="valid_data"
    )
    
    tester.test(num_test_images=50)
    
    print("\n💡 더 나은 양자화를 위한 팁:")
    print("   1. valid_data 디렉토리에 더 많은 이미지를 추가하세요")
    print("   2. Static quantization을 시도해보세요 (더 정확함)")
    print("   3. FP16 양자화도 고려해보세요 (정확도 손실 최소)")
