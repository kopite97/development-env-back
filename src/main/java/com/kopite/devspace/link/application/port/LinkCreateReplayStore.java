package com.kopite.devspace.link.application.port;
import com.kopite.devspace.link.application.model.LinkMutation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface LinkCreateReplayStore {
    record Replay(String requestHash, LinkMutation result, Instant expiresAt) {}
    Optional<Replay> find(UUID workspaceId, String key);
    void save(UUID workspaceId, String key, String requestHash, LinkMutation result, Instant now);
    void removeExpired(UUID workspaceId, Instant now);
}
