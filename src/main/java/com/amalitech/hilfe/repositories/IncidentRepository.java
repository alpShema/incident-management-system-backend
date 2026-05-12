package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
            WHERE i.userId = :userId
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            WHERE i.userId = :userId
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """)
    Page<Incident> findByUserIdFiltered(
            @Param("userId") String userId,
            @Param("statusId") String statusId,
            @Param("severityId") String severityId,
            @Param("incidentTypeId") String incidentTypeId,
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
            WHERE i.assignedToId = :agentId
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            WHERE i.assignedToId = :agentId
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """)
    Page<Incident> findByAssignedToIdFiltered(
            @Param("agentId") String agentId,
            @Param("statusId") String statusId,
            @Param("severityId") String severityId,
            @Param("incidentTypeId") String incidentTypeId,
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
            WHERE (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            WHERE (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """)
    Page<Incident> findAllFiltered(
            @Param("statusId") String statusId,
            @Param("severityId") String severityId,
            @Param("incidentTypeId") String incidentTypeId,
            @Param("locationId") String locationId,
            Pageable pageable
    );

    @Query("""
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            WHERE i.id = :id
            """)
    java.util.Optional<Incident> findByIdWithDetails(@Param("id") String id);
}
