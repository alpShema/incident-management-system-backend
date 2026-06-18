package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.SlackUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlackUserMappingRepository extends JpaRepository<SlackUserMapping, UUID> {

    Optional<SlackUserMapping> findBySlackUserId(String slackUserId);

    Optional<SlackUserMapping> findByHilfeUserId(String hilfeUserId);

    boolean existsBySlackUserId(String slackUserId);

    boolean existsByHilfeUserId(String hilfeUserId);

    @Modifying
    @Query("DELETE FROM SlackUserMapping s WHERE s.hilfeUserId = :hilfeUserId")
    void deleteByHilfeUserId(String hilfeUserId);

    @Modifying
    @Query("DELETE FROM SlackUserMapping s WHERE s.slackUserId = :slackUserId")
    void deleteBySlackUserId(String slackUserId);
}
