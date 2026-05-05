package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findAllByRoleCode(RoleCode roleCode);
}
