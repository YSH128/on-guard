USE shutter;

CREATE TABLE IF NOT EXISTS ward_locations (
    ward_id BIGINT PRIMARY KEY,
    lat DOUBLE NOT NULL,
    lng DOUBLE NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_sharing BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (ward_id) REFERENCES users(id)
);

CREATE INDEX idx_ward_locations_updated_at ON ward_locations (updated_at);

DELIMITER $$
CREATE PROCEDURE seed_bench_users(IN total_pairs INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= total_pairs DO
        -- Ward: IDs 1 .. total_pairs
        INSERT INTO users (id, kakao_id, name, role, device_uuid, gps_sharing)
        VALUES (i, 100000 + i, CONCAT('피보호자_', i), 'WARD', UUID(), TRUE)
        ON DUPLICATE KEY UPDATE gps_sharing = TRUE, role = 'WARD';

        -- Guardian: IDs 501 .. 500 + total_pairs
        INSERT INTO users (id, kakao_id, name, role, device_uuid, gps_sharing)
        VALUES (500 + i, 200000 + i, CONCAT('보호자_', i), 'GUARDIAN', UUID(), FALSE)
        ON DUPLICATE KEY UPDATE role = 'GUARDIAN';

        -- 1:1 Relationship
        INSERT IGNORE INTO user_relationships (guardian_id, ward_id)
        VALUES (500 + i, i);

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL seed_bench_users(500);
DROP PROCEDURE seed_bench_users;
