package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {
    boolean existsByNameIgnoreCase(String name);
    Page<Department> findByStatus(Boolean status, Pageable pageable);
}
