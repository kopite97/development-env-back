package com.kopite.devspace.user.infrastructure;

import com.kopite.devspace.user.domain.AuthIdentity;
import com.kopite.devspace.user.domain.AuthIdentityId;
import com.kopite.devspace.user.domain.AuthIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthIdentityRepositoryAdapter implements AuthIdentityRepository {

    private final AuthIdentityJpaRepository repository;

    @Override
    public AuthIdentity save(AuthIdentity identity) {
        return repository.save(identity);
    }

    @Override
    public Optional<AuthIdentity> findById(AuthIdentityId id) {
        return repository.findById(id);
    }
}
