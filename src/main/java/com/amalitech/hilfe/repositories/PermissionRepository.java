package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByCodeIn(Collection<String> codes);
    List<Permission> findAllByOrderByCodeAsc();
}
