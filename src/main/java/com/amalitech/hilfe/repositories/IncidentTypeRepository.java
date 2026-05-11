package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentTypeRepository extends JpaRepository<IncidentType, String> {
    List<IncidentType> findByCategoryId(String categoryId);
}
