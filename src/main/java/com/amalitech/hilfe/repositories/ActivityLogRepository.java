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
                al.id,
                actor.fullName,
                actor.profileImg,
                target.fullName,
                al.action,
                al.subjectType,
                incident.incidentNo,
                al.description,
                al.metadata,
                al.createdAt
            )
            FROM ActivityLog al
            LEFT JOIN al.actorUser actor
            LEFT JOIN al.targetUser target
            LEFT JOIN Incident incident ON incident.id = al.subjectId
            """)
    Page<ActivityLogResponse> findActivityLogResponses(Pageable pageable);

    @Query("""
            SELECT new com.amalitech.hilfe.dto.ActivityLogResponse(
                al.id,
                actor.fullName,
                actor.profileImg,
                target.fullName,
                al.action,
                al.subjectType,
                incident.incidentNo,
                al.description,
                al.metadata,
                al.createdAt
            )
            FROM ActivityLog al
            LEFT JOIN al.actorUser actor
            LEFT JOIN al.targetUser target
            LEFT JOIN Incident incident ON incident.id = al.subjectId
            WHERE al.subjectType = 'INCIDENT' AND al.subjectId = :incidentId
            """)
    Page<ActivityLogResponse> findActivityLogResponsesByIncidentId(@org.springframework.data.repository.query.Param("incidentId") String incidentId, Pageable pageable);

    @Query("""
            SELECT new com.amalitech.hilfe.dto.ActivityLogResponse(
                al.id,
                actor.fullName,
                actor.profileImg,
                target.fullName,
                al.action,
                al.subjectType,
                incident.incidentNo,
                al.description,
                al.metadata,
                al.createdAt
            )
            FROM ActivityLog al
            LEFT JOIN al.actorUser actor
            LEFT JOIN al.targetUser target
            LEFT JOIN Incident incident ON incident.id = al.subjectId
            ORDER BY al.createdAt DESC
            """)
    List<ActivityLogResponse> findRecent(Pageable pageable);
}
