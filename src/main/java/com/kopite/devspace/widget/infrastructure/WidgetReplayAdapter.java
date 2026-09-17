package com.kopite.devspace.widget.infrastructure;
import com.kopite.devspace.widget.application.WidgetReplayStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
@Repository @RequiredArgsConstructor
public class WidgetReplayAdapter implements WidgetReplayStore {
    private final JdbcTemplate jdbc;private final JsonMapper json;
    public Optional<Replay> find(UUID w,String path,String key) {
        return jdbc.query("select request_hash,response_body,location,expires_at from widget_operation_replays where workspace_id=? and method='POST' and path=? and key=?",
            (r,n)->new Replay(r.getString(1),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant()),w,path,key).stream().findFirst();
    }
    public Replay save(UUID w,String path,String key,String hash,Object result,String location,Instant now) {
        String body=json.writeValueAsString(result);Instant expiry=now.plusSeconds(86400);
        jdbc.update("insert into widget_operation_replays values(?,'POST',?,?,?,201,?,?,?,?)",w,path,key,hash,body,location,Timestamp.from(now),Timestamp.from(expiry));
        return new Replay(hash,body,location,expiry);
    }
    public void removeExpired(UUID w,Instant now){jdbc.update("delete from widget_operation_replays where workspace_id=? and expires_at<=?",w,Timestamp.from(now));}
}
