package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Faq;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, String> {

    @Query(value = "SELECT * FROM \"Faq\" WHERE embedding IS NULL", nativeQuery = true)
    List<Faq> findAllWithoutEmbedding();

    @Query("""
            SELECT f FROM Faq f
            WHERE (:active IS NULL OR f.active = :active)
            AND (:search IS NULL OR LOWER(f.question) LIKE :search
                                 OR LOWER(f.answer)   LIKE :search)
            """)
    Page<Faq> findAllFiltered(
            @Param("active") Boolean active,
            @Param("search") String search,
            Pageable pageable
    );

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE "Faq" SET embedding = CAST(:embedding AS vector), updated_at = NOW()
            WHERE id = :id
            """, nativeQuery = true)
    void updateEmbedding(@Param("id") String id, @Param("embedding") String embeddingVector);
}
