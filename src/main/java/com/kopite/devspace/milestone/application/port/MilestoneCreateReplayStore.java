package com.kopite.devspace.milestone.application.port;
import com.kopite.devspace.milestone.application.model.MilestoneSnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface MilestoneCreateReplayStore {
    record Replay(String requestHash, MilestoneSnapshot result, Instant expiresAt, boolean legacy) {
        public Replay(String requestHash, MilestoneSnapshot result, Instant expiresAt) { this(requestHash,result,expiresAt,false); }
    }
    Optional<Replay> find(UUID workspaceId, String key);
    void save(UUID workspaceId, String key, String requestHash, MilestoneSnapshot result, Instant now);
    void removeExpired(UUID workspaceId, Instant now);
}
