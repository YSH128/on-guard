CREATE DATABASE IF NOT EXISTS shutter DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE shutter;

-- 보호자/피보호자 통합 유저 테이블
CREATE TABLE users (
                       id INT AUTO_INCREMENT PRIMARY KEY,
                       kakao_id BIGINT NOT NULL UNIQUE,       -- 카카오 회원번호 (숫자형)
                       name VARCHAR(50) NOT NULL,             -- 지도에서 표시될 이름
                       role VARCHAR(10),             -- 'guardian' or 'ward'
                       created_at TIMESTAMP
);

-- 보호자-피보호자 N:M 관계 테이블 (user_guardians 대체)
CREATE TABLE user_relationships (
                                    id INT AUTO_INCREMENT PRIMARY KEY,
                                    guardian_id INT NOT NULL,
                                    ward_id INT NOT NULL,
                                    created_at TIMESTAMP,
                                    FOREIGN KEY (guardian_id) REFERENCES users(id),
                                    FOREIGN KEY (ward_id) REFERENCES users(id)
);
CREATE INDEX idx_user_relationships_ward_id ON user_relationships (ward_id);         -- 피싱 감지 시 보호자 조회 핵심
CREATE INDEX idx_user_relationships_guardian_id ON user_relationships (guardian_id);

CREATE TABLE ai_telemetry_logs (
                                   id INT AUTO_INCREMENT PRIMARY KEY,
                                   user_id INT NOT NULL,
                                   model_version VARCHAR(100) NOT NULL,
                                   model_type VARCHAR(20) NOT NULL,        -- FACE, STT
                                   inference_success BOOLEAN NOT NULL,
                                   failure_type VARCHAR(50),
                                   inference_latency_ms BIGINT NOT NULL,
                                   risk_score DOUBLE,
                                   timestamp TIMESTAMP NOT NULL,           -- 모바일 기준 추론 발생 시각
                                   device_hash VARCHAR(255) NOT NULL,
                                   session_id VARCHAR(255) NOT NULL,
                                   device_temperature FLOAT NOT NULL,
                                   app_pss_memory INT NOT NULL,
                                   thermal_throttling_status INT NOT NULL,
                                   thread_cpu_time BIGINT NOT NULL,
                                   battery_current_now FLOAT NOT NULL,
                                   model_load_latency_ms INT,
                                   FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_ai_telemetry_logs_user_id ON ai_telemetry_logs (user_id);
CREATE INDEX idx_ai_telemetry_logs_model_type ON ai_telemetry_logs (model_type);
CREATE INDEX idx_ai_telemetry_logs_model_version ON ai_telemetry_logs (model_version);
CREATE INDEX idx_ai_telemetry_logs_timestamp ON ai_telemetry_logs (timestamp);
CREATE INDEX idx_ai_telemetry_logs_inference_success ON ai_telemetry_logs (inference_success);

CREATE TABLE notification_logs (
                                   id INT AUTO_INCREMENT PRIMARY KEY,
                                   ward_id INT NOT NULL,                   -- 피싱이 감지된 피보호자 (user_id → ward_id로 명확화)
                                   guardian_id INT NOT NULL,              -- 알림을 받는 보호자
                                   status VARCHAR(20) NOT NULL,           -- SUCCESS, FAIL
                                   fail_reason VARCHAR(500),
                                   sent_at TIMESTAMP,
                                   FOREIGN KEY (ward_id) REFERENCES users(id),
                                   FOREIGN KEY (guardian_id) REFERENCES users(id)  -- guardians 테이블 삭제로 users 참조
);
CREATE INDEX idx_notification_logs_ward_id ON notification_logs (ward_id);
CREATE INDEX idx_notification_logs_guardian_id ON notification_logs (guardian_id);
CREATE INDEX idx_notification_logs_sent_at ON notification_logs (sent_at);