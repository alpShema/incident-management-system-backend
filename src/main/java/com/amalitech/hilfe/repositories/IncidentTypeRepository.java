package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentTypeRepository extends JpaRepository<IncidentType, String> {

    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT it FROM IncidentType it LEFT JOIN FETCH it.category WHERE it.categoryId = :categoryId")
    List<IncidentType> findByCategoryId(@Param("categoryId") String categoryId);

    @Query("""
            SELECT it FROM IncidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH ag.primaryAgent pa
            LEFT JOIN FETCH pa.user
            WHERE it.categoryId = :categoryId
            """)
    List<IncidentType> findByCategoryIdWithAgent(@Param("categoryId") String categoryId);

    @Query("""
            SELECT it FROM IncidentType it
            LEFT JOIN FETCH it.category
            LEFT JOIN FETCH it.agentGroup ag
            LEFT JOIN FETCH ag.primaryAgent pa
            LEFT JOIN FETCH pa.user
            WHERE it.id = :id
            """)
    java.util.Optional<IncidentType> findByIdWithDetails(@Param("id") String id);
}
