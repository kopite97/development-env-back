package com.kopite.devspace.widget.application;
import com.kopite.devspace.widget.domain.WidgetException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
/** Scoped replay coordination, always inside the caller's workspace-locked transaction. */
@Service @RequiredArgsConstructor
public class WidgetReplay {
    public static final String CREATE="/api/v1/widgets",INITIALIZE="/api/v3/dashboards/home/initializations";
    private final WidgetReplayStore store;
    public Optional<WidgetCreationResult> find(UUID w,String path,String key,String hash,Instant now) {
        if(key==null||!key.matches("[!-~]{1,128}"))throw WidgetException.invalid("Idempotency-Key");
        var previous=store.find(w,path,key);
        if(previous.isPresent()&&previous.get().expiresAt().isAfter(now)) {
            var p=previous.get();if(!hash.equals(p.hash()))throw new WidgetException("IDEMPOTENCY_KEY_REUSED",null);
            return Optional.of(new WidgetCreationResult(p.body(),p.location(),null));
        }
        return Optional.empty();
    }
    public WidgetCreationResult save(UUID w,String path,String key,String hash,Object body,String location,Instant now,long revision) {
        store.removeExpired(w,now);var p=store.save(w,path,key,hash,body,location,now);return new WidgetCreationResult(p.body(),location,revision);
    }
}
