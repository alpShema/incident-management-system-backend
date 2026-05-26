package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByNameIgnoreCase(String name);

    @Query("""
            SELECT r FROM Role r
            WHERE (:query IS NULL OR LOWER(r.name) LIKE :query ESCAPE '!' OR LOWER(r.code) LIKE :query ESCAPE '!')
            """)
    Page<Role> findAllFiltered(@Param("query") String query, Pageable pageable);
}
