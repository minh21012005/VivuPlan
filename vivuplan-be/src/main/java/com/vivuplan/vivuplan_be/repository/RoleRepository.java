package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(Role.RoleName name);
    boolean existsByName(Role.RoleName name);
}
