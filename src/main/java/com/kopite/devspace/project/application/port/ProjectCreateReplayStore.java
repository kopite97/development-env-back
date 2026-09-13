package com.kopite.devspace.project.application.port;
import com.kopite.devspace.project.application.model.ProjectSnapshot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCreateReplayStore {
    record Replay(String requestHash, ProjectSnapshot result, Instant expiresAt) {}

    Optional<Replay> find(UUID workspaceId, String key);
    void save(UUID workspaceId, String key, String requestHash, ProjectSnapshot result, Instant now);
    void removeExpired(UUID workspaceId, Instant now);
}
