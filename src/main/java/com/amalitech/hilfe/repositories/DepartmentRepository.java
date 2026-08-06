package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {
    boolean existsByNameIgnoreCase(String name);
    Page<Department> findByStatus(Boolean status, Pageable pageable);
    List<Department> findByHeadUserIdOrderByNameAsc(String headUserId);
    boolean existsByHeadUserId(String headUserId);

    @Query("""
            SELECT d FROM Department d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:queryPattern IS NULL OR (
                   LOWER(d.name) LIKE :queryPattern ESCAPE '!'
                OR LOWER(d.description) LIKE :queryPattern ESCAPE '!'
              ))
            """)
    Page<Department> search(@Param("queryPattern") String queryPattern,
                            @Param("status") Boolean status,
                            Pageable pageable);
}
