package com.kopite.devspace.milestone.domain;

import java.util.Optional;
import java.util.UUID;

public interface MilestoneRepository {
    Milestone save(Milestone milestone);
    void delete(Milestone milestone);
    Optional<Milestone> findOwned(UUID workspaceId, UUID id);
    Optional<Milestone> lockOwned(UUID workspaceId, UUID id);
}
