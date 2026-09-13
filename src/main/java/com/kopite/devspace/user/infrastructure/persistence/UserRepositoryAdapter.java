package com.kopite.devspace.user.infrastructure.persistence;

import com.kopite.devspace.user.domain.User;
import com.kopite.devspace.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;

    @Override
    public User save(User user) {
        return repository.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id);
    }
}
