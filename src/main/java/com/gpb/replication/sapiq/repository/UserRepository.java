package com.gpb.replication.sapiq.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gpb.replication.sapiq.model.UserEntity;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
}
