package com.kopite.devspace.journal.application.port;
import com.kopite.devspace.journal.application.model.JournalSnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface JournalCreateReplayStore {
    record Replay(String requestHash, JournalSnapshot result, Instant expiresAt) {}
    Optional<Replay> find(UUID workspaceId, String key);
    void save(UUID workspaceId, String key, String requestHash, JournalSnapshot result, Instant now);
    void removeExpired(UUID workspaceId, Instant now);
}
