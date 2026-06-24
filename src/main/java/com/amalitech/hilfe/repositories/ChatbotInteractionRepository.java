package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.ChatbotInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ChatbotInteractionRepository extends JpaRepository<ChatbotInteraction, String> {

    @Query(value = """
            SELECT * FROM "ChatbotInteraction"
            WHERE (:userId IS NULL OR user_id = :userId)
              AND (:outcome IS NULL OR outcome = :outcome)
              AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
            ORDER BY created_at DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM "ChatbotInteraction"
            WHERE (:userId IS NULL OR user_id = :userId)
              AND (:outcome IS NULL OR outcome = :outcome)
              AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
            """,
           nativeQuery = true)
    Page<ChatbotInteraction> findAllFiltered(
            @Param("userId") String userId,
            @Param("outcome") String outcome,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
