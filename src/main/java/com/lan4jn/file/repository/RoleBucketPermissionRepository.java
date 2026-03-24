package com.lan4jn.file.repository;

import com.lan4jn.file.entity.RoleBucketPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RoleBucketPermissionRepository extends JpaRepository<RoleBucketPermission, Long> {

    List<RoleBucketPermission> findByRoleId(Long roleId);

    List<RoleBucketPermission> findByRoleIdIn(Collection<Long> roleIds);

    void deleteByRoleId(Long roleId);

    boolean existsByRoleId(Long roleId);
}
