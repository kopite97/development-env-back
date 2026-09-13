package com.kopite.devspace.project.infrastructure.persistence;

import com.kopite.devspace.project.application.port.ProjectCreateReplayStore;
import com.kopite.devspace.project.application.model.ProjectSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProjectCreateReplayAdapter implements ProjectCreateReplayStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    @Override
    public Optional<Replay> find(UUID workspaceId, String key) {
        return jdbc.query("""
                select request_hash, response_body, expires_at from project_create_idempotency
                where workspace_id=? and method='POST' and path='/api/v1/projects' and key=?
                """, (rs, row) -> new Replay(rs.getString(1), json.readValue(rs.getString(2), ProjectSnapshot.class),
                rs.getTimestamp(3).toInstant()), workspaceId, key).stream().findFirst();
    }

    @Override
    public void save(UUID workspaceId, String key, String requestHash, ProjectSnapshot result, Instant now) {
        jdbc.update("""
                insert into project_create_idempotency
                (workspace_id,method,path,key,request_hash,response_status,response_body,created_at,expires_at)
                values(?,'POST','/api/v1/projects',?,?,201,?,?,?)
                """, workspaceId, key, requestHash, json.writeValueAsString(result), Timestamp.from(now),
                Timestamp.from(now.plus(Duration.ofHours(24))));
    }

    @Override
    public void removeExpired(UUID workspaceId, Instant now) {
        jdbc.update("delete from project_create_idempotency where workspace_id=? and expires_at<=?",
                workspaceId, Timestamp.from(now));
    }
}
