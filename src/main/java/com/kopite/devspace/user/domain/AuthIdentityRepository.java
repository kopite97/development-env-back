package com.kopite.devspace.user.domain;

import java.util.Optional;

public interface AuthIdentityRepository {

    AuthIdentity save(AuthIdentity identity);

    Optional<AuthIdentity> findById(AuthIdentityId id);
}
