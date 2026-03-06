CREATE DATABASE IF NOT EXISTS shutter DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE shutter;

CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  kakao_id VARCHAR(100) NOT NULL UNIQUE,
  created_at TIMESTAMP
);

CREATE TABLE guardians (
  id INT AUTO_INCREMENT PRIMARY KEY,
  kakao_id VARCHAR(100) NOT NULL UNIQUE,
  created_at TIMESTAMP
);

CREATE TABLE user_guardians (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  guardian_id INT NOT NULL,
  created_at TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (guardian_id) REFERENCES guardians(id)
);
CREATE INDEX idx_user_guardians_user_id ON user_guardians (user_id);
CREATE INDEX idx_user_guardians_guardian_id ON user_guardians (guardian_id);

CREATE TABLE oauth_tokens (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  access_token VARCHAR(500) NOT NULL,
  refresh_token VARCHAR(500),
  expires_at TIMESTAMP,
  created_at TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_oauth_tokens_user_id ON oauth_tokens (user_id);


CREATE TABLE ai_telemetry_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT,
  model_version VARCHAR(100) NOT NULL,
  model_type VARCHAR(20) NOT NULL,
  inference_success BOOLEAN NOT NULL,
  failure_type VARCHAR(50),
  inference_latency_ms BIGINT NOT NULL,
  risk_score DOUBLE,
  timestamp TIMESTAMP NOT NULL,
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
  user_id INT NOT NULL,
  guardian_id INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  fail_reason VARCHAR(500),
  sent_at TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (guardian_id) REFERENCES guardians(id)
);
CREATE INDEX idx_notification_logs_user_id ON notification_logs (user_id);
CREATE INDEX idx_notification_logs_guardian_id ON notification_logs (guardian_id);
CREATE INDEX idx_notification_logs_sent_at ON notification_logs (sent_at);
