package com.shutter.api.notification.repository;

import com.shutter.api.notification.domain.NotificationLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * 피보호자 ID를 기준으로 알림 로그 조회 (보호자 정보 포함, 최신순)
     */
    @EntityGraph(attributePaths = {"guardian"})
    List<NotificationLog> findAllByWardIdOrderBySentAtDesc(Long wardId);

    /**
     * 보호자 ID를 기준으로 알림 로그 조회 (피보호자 정보 포함, 최신순)
     */
    @EntityGraph(attributePaths = {"ward"})
    List<NotificationLog> findAllByGuardianIdOrderBySentAtDesc(Long guardianId);
}
