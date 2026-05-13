package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaRepository extends JpaRepository<Media, String> {

    List<Media> findByIncidentId(String incidentId);
}
