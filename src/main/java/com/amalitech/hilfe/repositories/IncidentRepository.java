package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.dto.IncidentDateFilter;
import com.amalitech.hilfe.dto.IncidentFilterParams;
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
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
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
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE (i.userId = :userId OR i.assignedToId = :agentId)
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            WHERE (i.userId = :userId OR i.assignedToId = :agentId)
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            """)
    Page<Incident> findByAgentScope(
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("filters") IncidentFilterParams filters,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
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
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:categoryId IS NULL OR it.categoryId = :categoryId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT i) FROM Incident i
            LEFT JOIN i.incidentType it
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            AND (:statusId IS NULL OR i.statusId = :statusId)
            AND (:severityId IS NULL OR i.severityId = :severityId)
            AND (:incidentTypeId IS NULL OR i.incidentTypeId = :incidentTypeId)
            AND (:categoryId IS NULL OR it.categoryId = :categoryId)
            AND (:locationId IS NULL OR i.locationId = :locationId)
            """)
    Page<Incident> findByDepartmentFiltered(
            @Param("agentGroupIds") List<String> agentGroupIds,
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
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '!'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
              OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(DISTINCT i) FROM Incident i
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '!'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
              OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!')
            """)
    Page<Incident> searchByDepartment(
            @Param("agentGroupIds") List<String> agentGroupIds,
            @Param("queryPattern") String queryPattern,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category ic
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE i.userId = :userId
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '!'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
              OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
              OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
              OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!')
            AND (:filterFrom = false OR i.createdAt >= :fromDate)
            AND (:filterTo = false OR i.createdAt < :toDate)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            LEFT JOIN it.category ic
            WHERE i.userId = :userId
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '!'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
              OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
              OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
              OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!')
            AND (:filterFrom = false OR i.createdAt >= :fromDate)
            AND (:filterTo = false OR i.createdAt < :toDate)
            """)
    Page<Incident> searchByUserId(
            @Param("userId") String userId,
            @Param("queryPattern") String queryPattern,
            @Param("fromDate") Instant fromDate,
            @Param("filterFrom") boolean filterFrom,
            @Param("toDate") Instant toDate,
            @Param("filterTo") boolean filterTo,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category ic
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE (i.userId = :userId OR i.assignedToId = :agentId)
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '!'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
              OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
              OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
              OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            LEFT JOIN it.category ic
            WHERE (i.userId = :userId OR i.assignedToId = :agentId)
            AND (LOWER(i.title) LIKE :queryPattern ESCAPE '!'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
              OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
              OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
              OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!')
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
            LEFT JOIN FETCH it.category ic
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE (LOWER(i.title) LIKE :queryPattern ESCAPE '!'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
              OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
              OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
              OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            LEFT JOIN it.category ic
            WHERE (LOWER(i.title) LIKE :queryPattern ESCAPE '!'
              OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
              OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
              OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
              OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!')
            """)
    Page<Incident> searchAll(
            @Param("queryPattern") String queryPattern,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category ic
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE i.userId = :userId
            AND (:queryPattern IS NULL OR (
                LOWER(i.title) LIKE :queryPattern ESCAPE '!'
                OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
                OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
                OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!'))
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            AND (:#{#dateFilter.filterFrom} = false OR i.createdAt >= :#{#dateFilter.fromDate})
            AND (:#{#dateFilter.filterTo} = false OR i.createdAt < :#{#dateFilter.toDate})
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            LEFT JOIN it.category ic
            WHERE i.userId = :userId
            AND (:queryPattern IS NULL OR (
                LOWER(i.title) LIKE :queryPattern ESCAPE '!'
                OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
                OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
                OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!'))
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            AND (:#{#dateFilter.filterFrom} = false OR i.createdAt >= :#{#dateFilter.fromDate})
            AND (:#{#dateFilter.filterTo} = false OR i.createdAt < :#{#dateFilter.toDate})
            """)
    Page<Incident> findByUserIdUnified(
            @Param("userId") String userId,
            @Param("queryPattern") String queryPattern,
            @Param("filters") IncidentFilterParams filters,
            @Param("dateFilter") IncidentDateFilter dateFilter,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category ic
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            AND (:queryPattern IS NULL OR (
                LOWER(i.title) LIKE :queryPattern ESCAPE '!'
                OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
                OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
                OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!'))
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            AND (:#{#dateFilter.filterFrom} = false OR i.createdAt >= :#{#dateFilter.fromDate})
            AND (:#{#dateFilter.filterTo} = false OR i.createdAt < :#{#dateFilter.toDate})
            """,
            countQuery = """
            SELECT COUNT(DISTINCT i) FROM Incident i
            LEFT JOIN i.incidentType it
            LEFT JOIN it.category ic
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            AND (:queryPattern IS NULL OR (
                LOWER(i.title) LIKE :queryPattern ESCAPE '!'
                OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
                OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
                OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!'))
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            AND (:#{#dateFilter.filterFrom} = false OR i.createdAt >= :#{#dateFilter.fromDate})
            AND (:#{#dateFilter.filterTo} = false OR i.createdAt < :#{#dateFilter.toDate})
            """)
    Page<Incident> findByDepartmentUnified(
            @Param("agentGroupIds") List<String> agentGroupIds,
            @Param("queryPattern") String queryPattern,
            @Param("filters") IncidentFilterParams filters,
            @Param("dateFilter") IncidentDateFilter dateFilter,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category ic
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE (:queryPattern IS NULL OR (
                LOWER(i.title) LIKE :queryPattern ESCAPE '!'
                OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
                OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
                OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!'))
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            AND (:#{#dateFilter.filterFrom} = false OR i.createdAt >= :#{#dateFilter.fromDate})
            AND (:#{#dateFilter.filterTo} = false OR i.createdAt < :#{#dateFilter.toDate})
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            LEFT JOIN it.category ic
            WHERE (:queryPattern IS NULL OR (
                LOWER(i.title) LIKE :queryPattern ESCAPE '!'
                OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
                OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
                OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!'))
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            AND (:#{#dateFilter.filterFrom} = false OR i.createdAt >= :#{#dateFilter.fromDate})
            AND (:#{#dateFilter.filterTo} = false OR i.createdAt < :#{#dateFilter.toDate})
            """)
    Page<Incident> findAllUnified(
            @Param("queryPattern") String queryPattern,
            @Param("filters") IncidentFilterParams filters,
            @Param("dateFilter") IncidentDateFilter dateFilter,
            Pageable pageable
    );

    @Query(value = """
            SELECT i FROM Incident i
            LEFT JOIN FETCH i.incidentType it
            LEFT JOIN FETCH it.category ic
            LEFT JOIN FETCH it.agent ita
            LEFT JOIN FETCH ita.user
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH i.location
            LEFT JOIN FETCH i.severity
            LEFT JOIN FETCH i.status
            LEFT JOIN FETCH i.createdBy
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE i.assignedToId = :agentId
            AND (:queryPattern IS NULL OR (
                LOWER(i.title) LIKE :queryPattern ESCAPE '!'
                OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
                OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
                OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!'))
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            AND (:#{#dateFilter.filterFrom} = false OR i.createdAt >= :#{#dateFilter.fromDate})
            AND (:#{#dateFilter.filterTo} = false OR i.createdAt < :#{#dateFilter.toDate})
            """,
            countQuery = """
            SELECT COUNT(i) FROM Incident i
            LEFT JOIN i.incidentType it
            LEFT JOIN it.category ic
            WHERE i.assignedToId = :agentId
            AND (:queryPattern IS NULL OR (
                LOWER(i.title) LIKE :queryPattern ESCAPE '!'
                OR LOWER(i.description) LIKE :queryPattern ESCAPE '!'
                OR LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(ic.name) LIKE :queryPattern ESCAPE '!'
                OR CAST(i.incidentNo AS string) LIKE :queryPattern ESCAPE '!'))
            AND (:#{#filters.statusId} IS NULL OR i.statusId = :#{#filters.statusId})
            AND (:#{#filters.severityId} IS NULL OR i.severityId = :#{#filters.severityId})
            AND (:#{#filters.incidentTypeId} IS NULL OR i.incidentTypeId = :#{#filters.incidentTypeId})
            AND (:#{#filters.categoryId} IS NULL OR it.categoryId = :#{#filters.categoryId})
            AND (:#{#filters.locationId} IS NULL OR i.locationId = :#{#filters.locationId})
            AND (:#{#dateFilter.filterFrom} = false OR i.createdAt >= :#{#dateFilter.fromDate})
            AND (:#{#dateFilter.filterTo} = false OR i.createdAt < :#{#dateFilter.toDate})
            """)
    Page<Incident> findByAssignedToIdUnified(
            @Param("agentId") String agentId,
            @Param("queryPattern") String queryPattern,
            @Param("filters") IncidentFilterParams filters,
            @Param("dateFilter") IncidentDateFilter dateFilter,
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
            LEFT JOIN FETCH i.assignedTo assignedAgent
            LEFT JOIN FETCH assignedAgent.user assignedUser
            LEFT JOIN FETCH assignedUser.location
            WHERE i.id = :id
            """)
    Optional<Incident> findByIdWithDetails(@Param("id") String id);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.assignedToId = :agentId")
    long countByAssignedToId(@Param("agentId") String agentId);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("""
            SELECT COUNT(DISTINCT i) FROM Incident i
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            """)
    long countByDepartment(@Param("agentGroupIds") List<String> agentGroupIds);

    @Query("""
            SELECT i.status.name, COUNT(i) FROM Incident i
            WHERE i.assignedToId = :agentId
            GROUP BY i.status.name
            """)
    List<Object[]> countByStatusForAgent(@Param("agentId") String agentId);

    @Query("""
            SELECT i.status.name, COUNT(i) FROM Incident i
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            GROUP BY i.status.name
            """)
    List<Object[]> countByStatusForDepartment(@Param("agentGroupIds") List<String> agentGroupIds);

    @Query("""
            SELECT i.status.name, COUNT(i) FROM Incident i
            WHERE i.assignedToId = :agentId
            AND i.createdAt >= :since
            GROUP BY i.status.name
            """)
    List<Object[]> countByStatusForAgentSince(@Param("agentId") String agentId, @Param("since") Instant since);

    @Query("""
            SELECT i.status.name, COUNT(i) FROM Incident i
            WHERE EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = i.assignedToId
                AND m.agentGroupId IN :agentGroupIds
            )
            AND i.createdAt >= :since
            GROUP BY i.status.name
            """)
    List<Object[]> countByStatusForDepartmentSince(@Param("agentGroupIds") List<String> agentGroupIds, @Param("since") Instant since);

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
            SELECT i.status.name, COUNT(DISTINCT i) FROM Incident i
            WHERE i.assignedToId = :agentId OR i.userId = :userId
            GROUP BY i.status.name
            """)
    List<Object[]> countByStatusForAgentCombined(@Param("agentId") String agentId, @Param("userId") String userId);

    @Query("""
            SELECT i.status.name, COUNT(DISTINCT i) FROM Incident i
            WHERE (i.assignedToId = :agentId OR i.userId = :userId)
            AND i.createdAt >= :since
            GROUP BY i.status.name
            """)
    List<Object[]> countByStatusForAgentCombinedSince(@Param("agentId") String agentId, @Param("userId") String userId, @Param("since") Instant since);

    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'Mon YYYY') AS month,
                   DATE_TRUNC('month', created_at) AS month_start,
                   COUNT(DISTINCT id) AS count
            FROM "Incident"
            WHERE (assigned_to_id = :agentId OR user_id = :userId)
            AND created_at >= :since
            GROUP BY DATE_TRUNC('month', created_at)
            ORDER BY DATE_TRUNC('month', created_at)
            """, nativeQuery = true)
    List<Object[]> countByMonthForAgentCombined(@Param("agentId") String agentId, @Param("userId") String userId, @Param("since") Instant since);

    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('month', i.created_at), 'Mon YYYY') AS month,
                   DATE_TRUNC('month', i.created_at) AS month_start,
                   COUNT(*) AS count
            FROM "Incident" i
            WHERE EXISTS (
                SELECT 1 FROM "AgentGroupMember" m
                WHERE m.agent_id = i.assigned_to_id
                AND m.agent_group_id IN (:agentGroupIds)
            )
            AND i.created_at >= :since
            GROUP BY DATE_TRUNC('month', i.created_at)
            ORDER BY DATE_TRUNC('month', i.created_at)
            """, nativeQuery = true)
    List<Object[]> countByMonthForDepartment(@Param("agentGroupIds") List<String> agentGroupIds, @Param("since") Instant since);

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

    @Query("""
            SELECT i FROM Incident i
            WHERE i.statusId = 'status-resolved'
            AND i.resolvedAt IS NOT NULL
            AND i.resolvedAt <= :cutoff
            """)
    List<Incident> findOverdueResolved(@Param("cutoff") Instant cutoff);

    boolean existsByIncidentTypeId(String incidentTypeId);

    boolean existsBySeverityId(String severityId);
}
