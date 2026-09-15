package com.kopite.devspace.projectcategory.infrastructure;
import com.kopite.devspace.projectcategory.application.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
@Repository @RequiredArgsConstructor
public class CategoryCreateReplayAdapter implements CategoryCreateReplayStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public Optional<Replay> find(UUID workspace,String key) {
        return jdbc.query("select request_hash,response_body,expires_at from project_category_create_idempotency where workspace_id=? and method='POST' and path='/api/v1/project-categories' and key=?",
            (rs,n)->new Replay(rs.getString(1),json.readValue(rs.getString(2),CategorySnapshot.class),rs.getTimestamp(3).toInstant()),workspace,key).stream().findFirst();
    }
    public void save(UUID workspace,String key,String hash,CategorySnapshot result,Instant now) {
        jdbc.update("insert into project_category_create_idempotency values(?,'POST','/api/v1/project-categories',?,?,201,?,?,?)",workspace,key,hash,json.writeValueAsString(result),Timestamp.from(now),Timestamp.from(now.plus(Duration.ofHours(24))));
    }
    public void removeExpired(UUID workspace,Instant now) { jdbc.update("delete from project_category_create_idempotency where workspace_id=? and expires_at<=?",workspace,Timestamp.from(now)); }
}
