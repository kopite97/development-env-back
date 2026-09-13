package com.kopite.devspace.user.infrastructure.persistence;

import com.kopite.devspace.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface UserJpaRepository extends JpaRepository<User, UUID> {
}
