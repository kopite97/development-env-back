package com.kopite.devspace.user.infrastructure.persistence;

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
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public AuthIdentity save(AuthIdentity identity) {
        // An assigned composite ID makes JpaRepository.save choose merge. A concurrent
        // winner must cause an INSERT conflict, never reassignment of its user_id.
        entityManager.persist(identity);
        entityManager.flush();
        return identity;
    }

    @Override
    public Optional<AuthIdentity> findById(AuthIdentityId id) {
        return repository.findById(id);
    }
}
