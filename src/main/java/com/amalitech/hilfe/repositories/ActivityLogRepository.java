package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.models.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    @Query("""
            SELECT new com.amalitech.hilfe.dto.ActivityLogResponse(
                activityLog.id,
                activityLog.actorUserId,
                activityLog.targetUserId,
                activityLog.action,
                activityLog.subjectType,
                activityLog.subjectId,
                activityLog.description,
                activityLog.metadata,
                activityLog.createdAt
            )
            FROM ActivityLog activityLog
            """)
    Page<ActivityLogResponse> findActivityLogResponses(Pageable pageable);

    @Query("""
            SELECT new com.amalitech.hilfe.dto.ActivityLogResponse(
                a.id, a.actorUserId, a.targetUserId,
                a.action, a.subjectType, a.subjectId,
                a.description, a.metadata, a.createdAt
            )
            FROM ActivityLog a
            ORDER BY a.createdAt DESC
            """)
    List<ActivityLogResponse> findRecent(Pageable pageable);
}
