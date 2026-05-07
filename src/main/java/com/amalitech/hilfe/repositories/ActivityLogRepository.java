package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.models.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
