package com.kopite.devspace.task.application.port;
import com.kopite.devspace.task.application.model.TaskSnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface TaskCreateReplayStore {
    record Replay(String requestHash, TaskSnapshot result, Instant expiresAt, boolean legacy) {
        public Replay(String requestHash, TaskSnapshot result, Instant expiresAt) { this(requestHash,result,expiresAt,false); }
    }
    Optional<Replay> find(UUID workspaceId, String key);
    void save(UUID workspaceId, String key, String requestHash, TaskSnapshot result, Instant now);
    void removeExpired(UUID workspaceId, Instant now);
}
