package com.kopite.devspace.widget.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.widget.domain.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class WidgetQueryService {
    private final CurrentUserService users;private final WidgetRepository widgets;private final WidgetReferences references;private final WidgetCursorCodec cursors;
    @io.swagger.v3.oas.annotations.media.Schema(name="WidgetConfigurationPage")
    public record Page(List<WidgetSnapshot> items,@io.swagger.v3.oas.annotations.media.Schema(nullable=true) String nextCursor,@JsonIgnore Long dataRevision){}
    public WidgetSnapshot get(UUID user,UUID id){var w=users.resolve(user).workspace();var widget=widgets.owned(w.getId(),id,false).orElseThrow(WidgetException::missing);return references.snapshots(w.getId(),List.of(widget),w.getDataRevision()).getFirst();}
    public Page list(UUID user,int limit,boolean unplaced,String cursor) {
        if(limit<1||limit>100)throw WidgetException.invalid("limit");var w=users.resolve(user).workspace();String context="list:"+limit+":"+unplaced+":createdAt-desc,id-desc";
        Instant at=null;UUID id=null;
        if(cursor!=null)try{var parts=cursors.decode(w.getId(),context,cursor).split("/",-1);if(parts.length!=2)throw new IllegalArgumentException();at=Instant.parse(parts[0]);id=UUID.fromString(parts[1]);}catch(IllegalArgumentException|java.time.DateTimeException e){throw new WidgetException("INVALID_CURSOR","cursor");}
        var rows=widgets.page(w.getId(),unplaced,limit,at,id);var page=rows.stream().limit(limit).toList();String next=null;
        if(rows.size()>limit){var last=page.getLast();next=cursors.encode(w.getId(),context,last.getCreatedAt()+"/"+last.getId());}
        return new Page(references.snapshots(w.getId(),page,w.getDataRevision()),next,w.getDataRevision());
    }
}
