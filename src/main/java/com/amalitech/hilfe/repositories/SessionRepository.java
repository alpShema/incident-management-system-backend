package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    @Modifying
    @Query("DELETE FROM Session s WHERE s.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}
