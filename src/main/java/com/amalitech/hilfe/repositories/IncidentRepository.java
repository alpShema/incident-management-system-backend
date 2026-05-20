package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            WHERE i.userId = :userId
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:categoryId IS NULL OR it.categoryId = :categoryId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            WHERE i.userId = :userId
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:categoryId IS NULL OR it.categoryId = :categoryId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """)
    Page<Incident> findByUserIdFiltered(
            @Param("userId") String userId,
            @Param("statusId") String statusId,
            @Param("severityId") String severityId,
            @Param("incidentTypeId") String incidentTypeId,
            @Param("categoryId") String categoryId,
            @Param("locationId") String locationId,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            WHERE (i.userId = :userId OR i.assignedToId = :agentId)
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:categoryId IS NULL OR it.categoryId = :categoryId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            WHERE (i.userId = :userId OR i.assignedToId = :agentId)
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:categoryId IS NULL OR it.categoryId = :categoryId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """)
    Page<Incident> findByAgentScope(
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("statusId") String statusId,
            @Param("severityId") String severityId,
            @Param("incidentTypeId") String incidentTypeId,
            @Param("categoryId") String categoryId,
            @Param("locationId") String locationId,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            WHERE (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:categoryId IS NULL OR it.categoryId = :categoryId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            WHERE (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:categoryId IS NULL OR it.categoryId = :categoryId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """)
    Page<Incident> findAllFiltered(
            @Param("statusId") String statusId,
            @Param("severityId") String severityId,
            @Param("incidentTypeId") String incidentTypeId,
            @Param("categoryId") String categoryId,
            @Param("locationId") String locationId,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            WHERE i.userId = :userId
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '\\'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '\\')
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            WHERE i.userId = :userId
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '\\'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '\\')
            """)
    Page<Incident> searchByUserId(
            @Param("userId") String userId,
            @Param("queryPattern") String queryPattern,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            WHERE (i.userId = :userId OR i.assignedToId = :agentId)
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '\\'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '\\')
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            WHERE (i.userId = :userId OR i.assignedToId = :agentId)
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '\\'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '\\')
            """)
    Page<Incident> searchByAgentScope(
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("queryPattern") String queryPattern,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            WHERE (LOWER(i.title) LIKE :queryPattern ESCAPE '\\'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '\\')
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            WHERE (LOWER(i.title) LIKE :queryPattern ESCAPE '\\'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '\\')
            """)
    Page<Incident> searchAll(
            @Param("queryPattern") String queryPattern,
            Pageable pageable
    );

    @Query("""
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            WHERE i.id = :id
            """)
    Optional<Incident> findByIdWithDetails(@Param("id") String id);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.assignedToId = :agentId")
    long countByAssignedToId(@Param("agentId") String agentId);

    @Query("""
            SELECT i.status.name, COUNT(i) FROM Incident i
            WHERE i.assignedToId = :agentId
            GROUP BY i.status.name
            """)
    List<Object[]> countByStatusForAgent(@Param("agentId") String agentId);

    @Query("""
            SELECT i.status.name, COUNT(i) FROM Incident i
            WHERE i.assignedToId = :agentId
            AND i.createdAt >= :since
            GROUP BY i.status.name
            """)
    List<Object[]> countByStatusForAgentSince(@Param("agentId") String agentId, @Param("since") Instant since);

    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'Mon YYYY') AS month,
                   DATE_TRUNC('month', created_at) AS month_start,
                   COUNT(*) AS count
            FROM "Incident"
            WHERE user_id = :userId
            AND created_at >= :since
            GROUP BY DATE_TRUNC('month', created_at)
            ORDER BY DATE_TRUNC('month', created_at)
            """, nativeQuery = true)
    List<Object[]> countByMonthForUser(@Param("userId") String userId, @Param("since") Instant since);

    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'Mon YYYY') AS month,
                   DATE_TRUNC('month', created_at) AS month_start,
                   COUNT(*) AS count
            FROM "Incident"
            WHERE assigned_to_id = :agentId
            AND created_at >= :since
            GROUP BY DATE_TRUNC('month', created_at)
            ORDER BY DATE_TRUNC('month', created_at)
            """, nativeQuery = true)
    List<Object[]> countByMonthForAgent(@Param("agentId") String agentId, @Param("since") Instant since);

    @Query("""
            SELECT COUNT(i) FROM Incident i
            WHERE i.assignedToId = :agentId
            AND LOWER(i.severity.name) IN ('high', 'critical')
            """)
    long countHighCriticalByAgent(@Param("agentId") String agentId);

    @Query("""
            SELECT COUNT(i) FROM Incident i
            WHERE i.assignedToId = :agentId
            AND i.read = false
            """)
    long countUnacknowledgedByAgent(@Param("agentId") String agentId);

    @Query("""
            SELECT COUNT(i) FROM Incident i
            WHERE i.assignedToId = :agentId
            AND LOWER(i.status.name) = 'closed'
            AND i.closedAt >= :since
            """)
    long countClosedSince(@Param("agentId") String agentId, @Param("since") Instant since);

    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (i.closed_at - i.created_at)) / 3600.0)
            FROM incident i
            JOIN status s ON s.id = i.status_id
            WHERE i.assigned_to_id = :agentId
            AND LOWER(s.name) = 'closed'
            AND i.closed_at >= :since
            """, nativeQuery = true)
    Double avgResolutionHoursSince(@Param("agentId") String agentId, @Param("since") Instant since);

    @Query("""
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            WHERE i.assignedToId = :agentId
            ORDER BY i.updatedAt DESC
            """)
    List<Incident> findRecentlyUpdatedByAgent(@Param("agentId") String agentId, Pageable pageable);

    // ── Admin dashboard queries ───────────────────────────────────────────────

    @Query("SELECT i.status.name, COUNT(i) FROM Incident i GROUP BY i.status.name")
    List<Object[]> countByStatusGlobal();

    @Query("SELECT i.status.name, COUNT(i) FROM Incident i WHERE i.createdAt >= :since GROUP BY i.status.name")
    List<Object[]> countByStatusSince(@Param("since") Instant since);

    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'Mon YYYY') AS month,
                   DATE_TRUNC('month', created_at) AS month_start,
                   COUNT(*) AS count
            FROM "Incident"
            WHERE created_at >= :since
            GROUP BY DATE_TRUNC('month', created_at)
            ORDER BY DATE_TRUNC('month', created_at)
            """, nativeQuery = true)
    List<Object[]> countByMonthSince(@Param("since") Instant since);

    @Query("""
            SELECT i.incidentType.category.name, COUNT(i)
            FROM Incident i
            GROUP BY i.incidentType.category.name
            """)
    List<Object[]> countByCategory();

    @Query("""
            SELECT i.incidentType.name, COUNT(i)
            FROM Incident i
            GROUP BY i.incidentType.name
            """)
    List<Object[]> countByTopic();

    @Query("""
            SELECT i.assignedTo.userId, COUNT(i)
            FROM Incident i
            WHERE i.assignedToId IS NOT NULL
            GROUP BY i.assignedTo.userId
            """)
    List<Object[]> countByAgent();

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.assignedToId IS NULL AND LOWER(i.status.name) NOT IN ('closed', 'resolved')")
    long countUnassigned();

    @Query("SELECT COUNT(i) FROM Incident i")
    long countTotal();
}
