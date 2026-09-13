package com.kopite.devspace.milestone.infrastructure.persistence;

import com.kopite.devspace.milestone.domain.Milestone;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface MilestoneJpaRepository extends JpaRepository<Milestone, UUID> {
    Optional<Milestone> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Milestone t where t.workspaceId = :workspaceId and t.id = :id")
    Optional<Milestone> lockOwned(UUID workspaceId, UUID id);
}
