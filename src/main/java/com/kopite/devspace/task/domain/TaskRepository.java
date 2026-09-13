package com.kopite.devspace.task.domain;

import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {
    Task save(Task task);
    Optional<Task> findOwned(UUID workspaceId, UUID id);
    Optional<Task> lockOwned(UUID workspaceId, UUID id);
}
