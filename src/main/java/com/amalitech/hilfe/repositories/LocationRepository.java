package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, String> {}
