package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    boolean existsByEmail(String email);
    @Query("select count(distinct u) from User u join u.roles r where r.name = :roleName")
    long countByRoleName(com.vivuplan.vivuplan_be.entity.Role.RoleName roleName);
}
