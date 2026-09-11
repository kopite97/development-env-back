package com.kopite.devspace.user.infrastructure;

import com.kopite.devspace.user.domain.AuthIdentity;
import com.kopite.devspace.user.domain.AuthIdentityId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface AuthIdentityJpaRepository extends JpaRepository<AuthIdentity, AuthIdentityId> {

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<AuthIdentity> findById(AuthIdentityId id);
}
