package com.kopite.devspace.projectcategory.application;
import java.time.Instant;
import java.util.*;
public interface CategoryCreateReplayStore {
    record Replay(String requestHash,CategorySnapshot result,Instant expiresAt) {}
    Optional<Replay> find(UUID workspace,String key);
    void save(UUID workspace,String key,String hash,CategorySnapshot result,Instant now);
    void removeExpired(UUID workspace,Instant now);
}
