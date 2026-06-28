package com.authen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.authen.entity.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Integer> {

    @Query("SELECT COUNT(p) > 0 FROM Permission p " +
           "JOIN p.role r " +
           "JOIN p.provider pr " +
           "WHERE r.roleName = :roleName " +
           "AND pr.method = :method " +
           "AND pr.api = :api " +
           "AND p.status = 'A' " +
           "AND pr.status = 'A'")
    boolean existsByRoleNameAndMethodAndApi(
            @Param("roleName") String roleName,
            @Param("method") String method,
            @Param("api") String api);
}
