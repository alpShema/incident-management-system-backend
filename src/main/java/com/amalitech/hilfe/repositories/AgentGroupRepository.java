package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.AgentGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentGroupRepository extends JpaRepository<AgentGroup, String> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByDepartmentIdAndStatus(String departmentId, Boolean status);
    List<AgentGroup> findByStatus(Boolean status);
    Page<AgentGroup> findByStatus(Boolean status, Pageable pageable);

    @Query("SELECT DISTINCT ag.departmentId FROM AgentGroup ag WHERE ag.id IN :groupIds AND ag.departmentId IS NOT NULL")
    List<String> findDepartmentIdsByGroupIds(@Param("groupIds") List<String> groupIds);

    @Query("SELECT ag.id FROM AgentGroup ag WHERE ag.departmentId IN :deptIds AND ag.status = true")
    List<String> findIdsByDepartmentIds(@Param("deptIds") List<String> deptIds);

    List<AgentGroup> findByDepartmentIdAndStatus(String departmentId, Boolean status);

    @Query("""
        SELECT ag FROM AgentGroup ag
        LEFT JOIN ag.department d
        WHERE ag.status = true
          AND (:query IS NULL OR LOWER(ag.name) LIKE :query OR LOWER(ag.description) LIKE :query OR LOWER(d.name) LIKE :query)
          AND (:departmentId IS NULL OR ag.departmentId = :departmentId)
        """)
    Page<AgentGroup> searchAgentGroups(
            @Param("query") String query,
            @Param("departmentId") String departmentId,
            Pageable pageable);

    @Query("""
        SELECT ag FROM AgentGroup ag
        LEFT JOIN ag.department d
        WHERE (:status IS NULL OR ag.status = :status)
          AND (:query IS NULL OR LOWER(ag.name) LIKE :query OR LOWER(ag.description) LIKE :query OR LOWER(d.name) LIKE :query)
          AND (:departmentId IS NULL OR ag.departmentId = :departmentId)
        """)
    Page<AgentGroup> listAllAgentGroups(
            @Param("status") Boolean status,
            @Param("query") String query,
            @Param("departmentId") String departmentId,
            Pageable pageable);
}
