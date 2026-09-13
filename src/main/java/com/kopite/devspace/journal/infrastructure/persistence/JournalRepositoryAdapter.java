package com.kopite.devspace.journal.infrastructure.persistence;

import com.kopite.devspace.journal.domain.Journal;
import com.kopite.devspace.journal.domain.JournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JournalRepositoryAdapter implements JournalRepository {
    private final JournalJpaRepository repository;

    public Journal save(Journal task) { return repository.save(task); }
    public void delete(Journal journal) { repository.delete(journal); }
    public Optional<Journal> findOwned(UUID workspaceId, UUID id) {
        return repository.findByWorkspaceIdAndId(workspaceId, id);
    }
    public Optional<Journal> lockOwned(UUID workspaceId, UUID id) {
        return repository.lockOwned(workspaceId, id);
    }
}
