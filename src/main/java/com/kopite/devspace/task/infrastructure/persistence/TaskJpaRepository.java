package com.kopite.devspace.task.infrastructure.persistence;

import com.kopite.devspace.task.domain.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface TaskJpaRepository extends JpaRepository<Task, UUID> {
    Optional<Task> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.workspaceId = :workspaceId and t.id = :id")
    Optional<Task> lockOwned(UUID workspaceId, UUID id);
}
