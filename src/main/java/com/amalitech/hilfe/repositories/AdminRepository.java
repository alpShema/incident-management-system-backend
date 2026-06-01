package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, String> {
    @Query("""
            SELECT a FROM Admin a
            LEFT JOIN FETCH a.user
            WHERE a.userId = :userId
            """)
    Optional<Admin> findByUserIdWithUser(@Param("userId") String userId);
}
