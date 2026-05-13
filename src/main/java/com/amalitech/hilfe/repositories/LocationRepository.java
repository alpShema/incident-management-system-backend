package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, String> {

    Optional<Location> findByNameIgnoreCase(String name);
}
