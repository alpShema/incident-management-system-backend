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
    List<AgentGroup> findByStatus(Boolean status);
    Page<AgentGroup> findByStatus(Boolean status, Pageable pageable);

    @Query("""
            SELECT g FROM AgentGroup g
            LEFT JOIN FETCH g.primaryAgent pa
            LEFT JOIN FETCH pa.user
            WHERE g.id = :id
            """)
    java.util.Optional<AgentGroup> findByIdWithPrimaryAgent(@Param("id") String id);

    @Query("""
            SELECT g FROM AgentGroup g
            LEFT JOIN FETCH g.primaryAgent pa
            LEFT JOIN FETCH pa.user
            WHERE g.status = :status
            """)
    List<AgentGroup> findByStatusWithPrimaryAgent(@Param("status") Boolean status);
}
