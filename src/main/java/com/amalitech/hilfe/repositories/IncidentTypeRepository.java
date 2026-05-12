package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentTypeRepository extends JpaRepository<IncidentType, String> {

    @Query("SELECT it FROM IncidentType it LEFT JOIN FETCH it.category WHERE it.categoryId = :categoryId")
    List<IncidentType> findByCategoryId(@Param("categoryId") String categoryId);
}
