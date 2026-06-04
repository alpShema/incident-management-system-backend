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
}
