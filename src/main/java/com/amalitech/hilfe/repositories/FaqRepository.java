package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Faq;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FaqRepository extends JpaRepository<Faq, String> {

    @Query("""
            SELECT f FROM Faq f
            WHERE (:active IS NULL OR f.active = :active)
            """)
    Page<Faq> findAllFiltered(
            @Param("active") Boolean active,
            Pageable pageable
    );

    @Query(value = """
            SELECT * FROM "Faq"
            WHERE active = TRUE AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT 1
            """, nativeQuery = true)
    Optional<Faq> findClosestActive(@Param("embedding") String embeddingVector);

    @Query(value = """
            SELECT (embedding <=> CAST(:embedding AS vector))
            FROM "Faq"
            WHERE active = TRUE AND embedding IS NOT NULL
            ORDER BY 1
            LIMIT 1
            """, nativeQuery = true)
    java.util.Optional<Double> findClosestActiveDistance(@Param("embedding") String embeddingVector);

    @Modifying
    @Query(value = """
            UPDATE "Faq" SET embedding = CAST(:embedding AS vector), updated_at = NOW()
            WHERE id = :id
            """, nativeQuery = true)
    void updateEmbedding(@Param("id") String id, @Param("embedding") String embeddingVector);
}
