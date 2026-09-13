package com.kopite.devspace.project.infrastructure.persistence;

import com.kopite.devspace.project.domain.Project;
import com.kopite.devspace.project.domain.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryAdapter implements ProjectRepository {
    private final ProjectJpaRepository repository;

    @Override
    public Project save(Project project) { return repository.save(project); }

    @Override
    public Optional<Project> findOwned(UUID workspaceId, UUID id) {
        return repository.findByWorkspaceIdAndId(workspaceId, id);
    }

    @Override
    public Optional<Project> lockOwned(UUID workspaceId, UUID id) {
        return repository.lockOwned(workspaceId, id);
    }
}
