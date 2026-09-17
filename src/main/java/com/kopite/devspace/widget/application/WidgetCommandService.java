package com.kopite.devspace.widget.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.workspace.domain.*;
import com.kopite.devspace.workspace.application.DeletedResource;
import com.kopite.devspace.widget.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class WidgetCommandService {
    private final CurrentUserService users;private final PersonalWorkspaceRepository workspaces;
    private final WidgetRepository widgets;private final WidgetTypeRegistry registry;private final WidgetReferences references;
    private final WidgetReplay replay;private final Clock projectClock;
    public WidgetCreationResult create(UUID user,String key,String type,String title,int version,String config,String hash) {
        var d=registry.get(type,version);var c=d.decode(config);Widget.title(title);
        var w=workspace(user);var now=projectClock.instant().truncatedTo(ChronoUnit.MICROS);
        var cached=replay.find(w.getId(),WidgetReplay.CREATE,key,hash,now);if(cached.isPresent())return cached.get();
        references.validate(w.getId(),d.selection(c),null);capacity(w);
        var widget=Widget.create(w.getId(),type,title,version,WidgetJson.MAPPER.writeValueAsString(c),now);widgets.insert(widget);w.recordBusinessMutation();widgets.flush();
        var snapshot=WidgetSnapshot.from(widget,c,false,w.getDataRevision());
        return replay.save(w.getId(),WidgetReplay.CREATE,key,hash,snapshot,WidgetReplay.CREATE+"/"+widget.getId(),now,w.getDataRevision());
    }
    public WidgetSnapshot replace(UUID user,UUID id,long revision,String title,int version,String config) {
        var w=workspace(user);var widget=widgets.owned(w.getId(),id,true).orElseThrow(WidgetException::missing);
        var d=registry.get(widget.getType(),version);var c=d.decode(config);
        var previous=registry.get(widget.getType(),widget.getConfigVersion());
        references.validate(w.getId(),d.selection(c),previous.selection(previous.decode(widget.getConfig())));
        widget.check(revision);capacity(w);widget.replace(revision,title,version,WidgetJson.MAPPER.writeValueAsString(c),projectClock.instant());
        w.recordBusinessMutation();widgets.flush();return references.snapshots(w.getId(),List.of(widget),w.getDataRevision()).getFirst();
    }
    public DeletedResource delete(UUID user,UUID id,long revision) {
        var w=workspace(user);var widget=widgets.owned(w.getId(),id,true).orElseThrow(WidgetException::missing);
        Widget.revision(revision,false);if(widget.getRevision()!=revision)throw WidgetException.conflict();
        if(widgets.placed(w.getId(),id))throw new WidgetException("WIDGET_IN_USE",null);
        capacity(w);widgets.delete(widget);w.recordBusinessMutation();widgets.flush();return new DeletedResource(id,w.getDataRevision());
    }
    private PersonalWorkspace workspace(UUID user){var w=workspaces.lockByOwnerId(user).orElseThrow(WidgetException::missing);users.resolve(user);return w;}
    private void capacity(PersonalWorkspace w){if(w.getDataRevision()==Long.MAX_VALUE)throw WidgetException.conflict();}
}
