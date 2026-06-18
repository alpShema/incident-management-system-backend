package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.SlackAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SlackAuditLogRepository extends JpaRepository<SlackAuditLog, Long> {

    Page<SlackAuditLog> findByHilfeUserId(String hilfeUserId, Pageable pageable);

    Page<SlackAuditLog> findBySlackUserId(String slackUserId, Pageable pageable);

    Page<SlackAuditLog> findByAction(String action, Pageable pageable);

    @Modifying
    @Query("DELETE FROM SlackAuditLog s WHERE s.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
