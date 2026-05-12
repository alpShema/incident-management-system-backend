package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {

    @Query("""
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            WHERE i.userId = :userId
            """)
    Page<Incident> findByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("""
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            WHERE i.userId = :userId OR i.assignedToId = :agentId
            """)
    Page<Incident> findByUserIdOrAssignedToId(@Param("userId") String userId, @Param("agentId") String agentId, Pageable pageable);

    @Query("""
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            """)
    Page<Incident> findAllWithDetails(Pageable pageable);

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
