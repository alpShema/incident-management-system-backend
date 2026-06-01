package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface LocationRepository extends JpaRepository<Location, String> {

    Optional<Location> findByNameIgnoreCase(String name);
}
