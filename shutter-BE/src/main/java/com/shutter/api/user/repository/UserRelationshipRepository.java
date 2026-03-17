package com.shutter.api.user.repository;

import com.shutter.api.user.domain.UserRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRelationshipRepository extends JpaRepository<UserRelationship, Long> {

    @Query("SELECT r FROM UserRelationship r JOIN FETCH r.ward WHERE r.guardian.id = :guardianId")
    List<UserRelationship> findByGuardianIdWithWard(@Param("guardianId") Long guardianId);

    @Query("SELECT r FROM UserRelationship r JOIN FETCH r.guardian WHERE r.ward.id = :wardId")
    List<UserRelationship> findByWardIdWithGuardian(@Param("wardId") Long wardId);

    @Query("SELECT r FROM UserRelationship r JOIN FETCH r.guardian JOIN FETCH r.ward WHERE r.id = :id")
    Optional<UserRelationship> findByIdWithUsers(@Param("id") Long id);
}