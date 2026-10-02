package com.example.backend.system.account.repository;

import com.example.backend.system.account.model.entity.Permission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    List<Permission> findByCodeIn(Collection<String> codes);
}
