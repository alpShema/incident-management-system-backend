package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.IncidentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentCategoryRepository extends JpaRepository<IncidentCategory, String> {
    boolean existsByNameIgnoreCase(String name);
}
