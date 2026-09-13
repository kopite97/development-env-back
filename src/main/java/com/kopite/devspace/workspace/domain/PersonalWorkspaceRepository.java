package com.kopite.devspace.workspace.domain;

import java.util.Optional;
import java.util.UUID;

public interface PersonalWorkspaceRepository {

    PersonalWorkspace save(PersonalWorkspace workspace);

    Optional<PersonalWorkspace> findById(UUID id);

    Optional<PersonalWorkspace> findByOwnerId(UUID ownerId);

    Optional<PersonalWorkspace> lockByOwnerId(UUID ownerId);
}
