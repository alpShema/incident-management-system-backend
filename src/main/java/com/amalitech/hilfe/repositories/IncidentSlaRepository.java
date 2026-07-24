package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.IncidentSla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface IncidentSlaRepository extends JpaRepository<IncidentSla, String> {

    List<IncidentSla> findByIncidentIdIn(Collection<String> incidentIds);

    @Query("""
            SELECT sla
            FROM IncidentSla sla
            JOIN FETCH sla.incident incident
            WHERE sla.responseDueAt IS NOT NULL
              AND sla.firstResponseAt IS NULL
              AND sla.resolvedAtSnapshot IS NULL
              AND sla.pauseStartedAt IS NULL
            """)
    List<IncidentSla> findActiveResponseTimers();

    @Query("""
            SELECT sla
            FROM IncidentSla sla
            JOIN FETCH sla.incident incident
            WHERE sla.resolutionDueAt IS NOT NULL
              AND sla.resolvedAtSnapshot IS NULL
              AND sla.pauseStartedAt IS NULL
            """)
    List<IncidentSla> findActiveResolutionTimers();

    @Query("""
            SELECT sla
            FROM IncidentSla sla
            JOIN FETCH sla.incident incident
            LEFT JOIN FETCH incident.severity severity
            WHERE (:filterFrom = false OR incident.createdAt >= :from)
              AND (:filterTo = false OR incident.createdAt <= :to)
              AND (:filterSeverity = false OR incident.severityId = :severityId)
            """)
    List<IncidentSla> findForReport(
            @Param("from") Instant from,
            @Param("filterFrom") boolean filterFrom,
            @Param("to") Instant to,
            @Param("filterTo") boolean filterTo,
            @Param("severityId") String severityId,
            @Param("filterSeverity") boolean filterSeverity
    );
}
