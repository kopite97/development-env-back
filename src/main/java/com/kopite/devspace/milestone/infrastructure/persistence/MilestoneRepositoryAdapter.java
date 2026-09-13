package com.kopite.devspace.milestone.infrastructure.persistence;

import com.kopite.devspace.milestone.domain.Milestone;
import com.kopite.devspace.milestone.domain.MilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MilestoneRepositoryAdapter implements MilestoneRepository {
    private final MilestoneJpaRepository repository;

    public Milestone save(Milestone milestone) { return repository.save(milestone); }
    public void delete(Milestone milestone) { repository.delete(milestone); }
    public Optional<Milestone> findOwned(UUID workspaceId, UUID id) {
        return repository.findByWorkspaceIdAndId(workspaceId, id);
    }
    public Optional<Milestone> lockOwned(UUID workspaceId, UUID id) {
        return repository.lockOwned(workspaceId, id);
    }
}
