package com.kopite.devspace.project.infrastructure.persistence;

import com.kopite.devspace.project.domain.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface ProjectJpaRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.workspaceId = :workspaceId and p.id = :id")
    Optional<Project> lockOwned(UUID workspaceId, UUID id);
}
