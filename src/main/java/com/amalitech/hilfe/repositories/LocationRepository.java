package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, String> {

    Optional<Location> findByNameIgnoreCase(String name);

    @Query("""
            SELECT l FROM Location l
            WHERE (:query IS NULL OR LOWER(l.name) LIKE :query OR LOWER(l.description) LIKE :query)
              AND (:status IS NULL OR l.status = :status)
            """)
    Page<Location> findFiltered(
            @Param("query") String query,
            @Param("status") Boolean status,
            Pageable pageable
    );
}
