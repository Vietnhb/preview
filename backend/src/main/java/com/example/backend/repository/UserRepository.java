package com.example.backend.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.backend.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    @Query("select u from User u join u.role r where lower(r.name) = lower(:roleName) and (u.active = true or u.active is null) order by u.fullName")
    List<User> findActiveByRoleName(@Param("roleName") String roleName);
}
