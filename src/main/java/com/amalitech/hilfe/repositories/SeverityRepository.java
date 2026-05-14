package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeverityRepository extends JpaRepository<Severity, String> {

    Optional<Severity> findByNameIgnoreCase(String name);
}
