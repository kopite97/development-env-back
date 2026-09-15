package com.kopite.devspace.project.domain;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    Project save(Project project);
    Optional<Project> findOwned(UUID workspaceId, UUID id);
    Optional<Project> lockOwned(UUID workspaceId, UUID id);
    java.util.List<Project> findOwnedByIds(UUID workspaceId, java.util.Set<UUID> ids);
}
