package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.IncidentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentTypeRepository extends JpaRepository<IncidentType, String> {

    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT it FROM IncidentType it LEFT JOIN FETCH it.category WHERE it.categoryId = :categoryId")
    List<IncidentType> findByCategoryId(@Param("categoryId") String categoryId);

    @Query("""
            SELECT it FROM IncidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH it.agentGroup ag
            WHERE it.categoryId = :categoryId
            """)
    List<IncidentType> findByCategoryIdWithAgent(@Param("categoryId") String categoryId);

    @Query("""
            SELECT it FROM IncidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH it.agentGroup ag
            WHERE it.id = :id
            """)
    java.util.Optional<IncidentType> findByIdWithDetails(@Param("id") String id);

    @Query(
            value = """
                    SELECT it FROM IncidentType it
                    LEFT JOIN FETCH it.category c
                    LEFT JOIN FETCH it.agentGroup ag
                    WHERE (:categoryId IS NULL OR it.categoryId = :categoryId)
                      AND (:departmentId IS NULL OR c.departmentId = :departmentId)
                      AND (:agentGroupId IS NULL OR it.agentGroupId = :agentGroupId)
                      AND (:status IS NULL OR LOWER(c.status) = LOWER(:status))
                      AND (:queryPattern IS NULL OR (
                           LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(it.description) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(c.name) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(ag.name) LIKE :queryPattern ESCAPE '!'
                      ))
                    """,
            countQuery = """
                    SELECT COUNT(it) FROM IncidentType it
                    LEFT JOIN it.category c
                    LEFT JOIN it.agentGroup ag
                    WHERE (:categoryId IS NULL OR it.categoryId = :categoryId)
                      AND (:departmentId IS NULL OR c.departmentId = :departmentId)
                      AND (:agentGroupId IS NULL OR it.agentGroupId = :agentGroupId)
                      AND (:status IS NULL OR LOWER(c.status) = LOWER(:status))
                      AND (:queryPattern IS NULL OR (
                           LOWER(it.name) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(it.description) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(c.name) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(ag.name) LIKE :queryPattern ESCAPE '!'
                      ))
                    """
    )
    Page<IncidentType> findAllTopicsFiltered(
            @Param("categoryId") String categoryId,
            @Param("departmentId") String departmentId,
            @Param("agentGroupId") String agentGroupId,
            @Param("status") String status,
            @Param("queryPattern") String queryPattern,
            Pageable pageable
    );
}
