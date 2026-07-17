package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.IncidentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface IncidentCategoryRepository extends JpaRepository<IncidentCategory, String> {
    boolean existsByNameIgnoreCase(String name);
    List<IncidentCategory> findByStatus(Boolean status);
    List<IncidentCategory> findByDepartmentIdAndStatus(String departmentId, Boolean status);
    boolean existsByDepartmentId(String departmentId);

    @Query("""
            SELECT c FROM IncidentCategory c
            LEFT JOIN FETCH c.department
            WHERE c.status = :status
            """)
    List<IncidentCategory> findByStatusWithDepartment(@Param("status") Boolean status);

    @Query("""
            SELECT c FROM IncidentCategory c
            LEFT JOIN FETCH c.department d
            WHERE c.status = :status
              AND (:queryPattern IS NULL
                   OR LOWER(c.name) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(c.description) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(d.name) LIKE :queryPattern ESCAPE '!')
            """)
    List<IncidentCategory> findByStatusWithDepartmentAndQuery(
            @Param("status") Boolean status,
            @Param("queryPattern") String queryPattern);

    @Query(value = """
            SELECT c FROM IncidentCategory c
            LEFT JOIN FETCH c.department d
            WHERE (:status IS NULL OR c.status = :status)
              AND (:queryPattern IS NULL
                   OR LOWER(c.name) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(c.description) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(d.name) LIKE :queryPattern ESCAPE '!')
              AND (:requireActiveTopics = FALSE OR EXISTS (
                    SELECT 1 FROM IncidentType t
                    WHERE t.categoryId = c.id AND t.status = TRUE
              ))
            """,
            countQuery = """
            SELECT COUNT(c) FROM IncidentCategory c
            LEFT JOIN c.department d
            WHERE (:status IS NULL OR c.status = :status)
              AND (:queryPattern IS NULL
                   OR LOWER(c.name) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(c.description) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(d.name) LIKE :queryPattern ESCAPE '!')
              AND (:requireActiveTopics = FALSE OR EXISTS (
                    SELECT 1 FROM IncidentType t
                    WHERE t.categoryId = c.id AND t.status = TRUE
              ))
            """)
    Page<IncidentCategory> findByStatusWithDepartmentAndQueryPaged(
            @Param("status") Boolean status,
            @Param("requireActiveTopics") boolean requireActiveTopics,
            @Param("queryPattern") String queryPattern,
            Pageable pageable);

    @Query(value = """
            SELECT c FROM IncidentCategory c
            LEFT JOIN FETCH c.department d
            WHERE c.status = TRUE
            AND (:queryPattern IS NULL
                OR LOWER(c.name) LIKE :queryPattern
                OR LOWER(c.description) LIKE :queryPattern
                OR LOWER(d.name) LIKE :queryPattern)
            """,
            countQuery = """
            SELECT COUNT(c) FROM IncidentCategory c
            LEFT JOIN c.department d
            WHERE c.status = TRUE
            AND (:queryPattern IS NULL
                OR LOWER(c.name) LIKE :queryPattern
                OR LOWER(c.description) LIKE :queryPattern
                OR LOWER(d.name) LIKE :queryPattern)
            """)
    Page<IncidentCategory> searchCategories(@Param("queryPattern") String queryPattern, Pageable pageable);

    @Query(value = """
            SELECT c FROM IncidentCategory c
            LEFT JOIN FETCH c.department d
            WHERE (:status IS NULL OR c.status = :status)
              AND (:queryPattern IS NULL
                   OR LOWER(c.name) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(c.description) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(d.name) LIKE :queryPattern ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(c) FROM IncidentCategory c
            LEFT JOIN c.department d
            WHERE (:status IS NULL OR c.status = :status)
              AND (:queryPattern IS NULL
                   OR LOWER(c.name) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(c.description) LIKE :queryPattern ESCAPE '!'
                   OR LOWER(d.name) LIKE :queryPattern ESCAPE '!')
            """)
    Page<IncidentCategory> findAllWithDepartmentAndQueryPaged(
            @Param("status") Boolean status,
            @Param("queryPattern") String queryPattern,
            Pageable pageable);

    @Query("""
            SELECT c FROM IncidentCategory c
            LEFT JOIN FETCH c.department
            WHERE c.id = :id
            """)
    java.util.Optional<IncidentCategory> findByIdWithDepartment(@Param("id") String id);

    @Query("""
            SELECT c FROM IncidentCategory c
            LEFT JOIN FETCH c.department
            WHERE c.departmentId = :departmentId AND c.status = :status
            """)
    List<IncidentCategory> findByDepartmentIdAndStatusWithDepartment(
            @Param("departmentId") String departmentId,
            @Param("status") Boolean status);
}
