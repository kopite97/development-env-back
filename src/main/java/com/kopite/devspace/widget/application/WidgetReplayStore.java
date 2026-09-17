package com.kopite.devspace.widget.application;
import java.time.Instant;
import java.util.*;
public interface WidgetReplayStore {
    record Replay(String hash,String body,String location,Instant expiresAt){}
    Optional<Replay> find(UUID workspace,String path,String key);
    Replay save(UUID workspace,String path,String key,String hash,Object result,String location,Instant now);
    void removeExpired(UUID workspace,Instant now);
}
