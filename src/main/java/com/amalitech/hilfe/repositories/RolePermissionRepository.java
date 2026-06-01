package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.RolePermission;
import com.amalitech.hilfe.models.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    @Query("""
            select permission.code
            from RolePermission rolePermission
            join rolePermission.permission permission
            where rolePermission.roleCode = :roleCode
            """)
    List<String> findPermissionCodesByRoleCode(@Param("roleCode") String roleCode);

    default List<String> findPermissionCodesByRoleCode(RoleCode roleCode) {
        return findPermissionCodesByRoleCode(roleCode == null ? null : roleCode.name());
    }

    @Query("""
            select rolePermission
            from RolePermission rolePermission
            join fetch rolePermission.permission
            where rolePermission.roleCode = :roleCode
            """)
    List<RolePermission> findByRoleCodeWithPermission(@Param("roleCode") String roleCode);

    void deleteByRoleCode(String roleCode);
}
