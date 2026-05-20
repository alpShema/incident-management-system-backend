package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.IncidentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentCategoryRepository extends JpaRepository<IncidentCategory, String> {
    boolean existsByNameIgnoreCase(String name);
    List<IncidentCategory> findByStatus(String status);
    List<IncidentCategory> findByDepartmentIdAndStatus(String departmentId, String status);
    boolean existsByDepartmentId(String departmentId);

    @Query("""
            SELECT c FROM IncidentCategory c
            LEFT JOIN FETCH c.department
            WHERE c.status = :status
            """)
    List<IncidentCategory> findByStatusWithDepartment(@Param("status") String status);

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
            @Param("status") String status);
}
