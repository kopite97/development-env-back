package com.kopite.devspace.workspace.infrastructure;

import com.kopite.devspace.workspace.domain.PersonalWorkspace;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PersonalWorkspaceRepositoryAdapter implements PersonalWorkspaceRepository {

    private final PersonalWorkspaceJpaRepository repository;

    @Override
    public PersonalWorkspace save(PersonalWorkspace workspace) {
        return repository.save(workspace);
    }

    @Override
    public Optional<PersonalWorkspace> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<PersonalWorkspace> findByOwnerId(UUID ownerId) {
        return repository.findByOwner_Id(ownerId);
    }

    @Override
    public Optional<PersonalWorkspace> lockByOwnerId(UUID ownerId) {
        return repository.lockByOwnerId(ownerId);
    }
}
