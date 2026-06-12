package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.sender WHERE m.incidentId = :incidentId")
    Page<Message> findByIncidentId(@Param("incidentId") String incidentId, Pageable pageable);

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.sender WHERE m.id = :id")
    java.util.Optional<Message> findByIdWithSender(@Param("id") String id);
}
