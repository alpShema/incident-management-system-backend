package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeverityRepository extends JpaRepository<Severity, String> {}
