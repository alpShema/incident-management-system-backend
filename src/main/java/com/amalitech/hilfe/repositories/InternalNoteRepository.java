package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.InternalNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InternalNoteRepository extends JpaRepository<InternalNote, String> {

    @Query("SELECT n FROM InternalNote n LEFT JOIN FETCH n.author WHERE n.incidentId = :incidentId")
    Page<InternalNote> findByIncidentId(@Param("incidentId") String incidentId, Pageable pageable);

    @Query("SELECT n FROM InternalNote n LEFT JOIN FETCH n.author WHERE n.id = :id")
    Optional<InternalNote> findByIdWithAuthor(@Param("id") String id);
}
