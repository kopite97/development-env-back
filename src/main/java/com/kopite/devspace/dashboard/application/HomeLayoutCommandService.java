package com.kopite.devspace.dashboard.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.widget.domain.*;
import com.kopite.devspace.widget.application.*;
import com.kopite.devspace.workspace.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor @Transactional
public class HomeLayoutCommandService {
    public record PlacementInput(UUID widgetId,String size){}
    private final CurrentUserService users;private final PersonalWorkspaceRepository workspaces;private final HomeLayoutRepository layouts;
    private final WidgetRepository widgets;private final WidgetTypeRegistry registry;private final WidgetReferences references;
    private final WidgetReplay replay;private final HomeLayoutQueryService queries;private final Clock projectClock;
    public HomeLayoutSnapshot replace(UUID user,long revision,List<PlacementInput> requested) {
        Widget.revision(revision,true);if(requested==null)throw WidgetException.invalid("placements");
        var ids=new HashSet<UUID>();for(var p:requested)if(p==null||p.widgetId()==null||!ids.add(p.widgetId()))throw WidgetException.invalid("placements");
        var w=workspace(user);var d=layouts.find(w.getId(),true);var values=widgets.owned(w.getId(),ids);
        if(values.size()!=ids.size())throw WidgetException.missing();references.snapshots(w.getId(),values,w.getDataRevision());
        var byId=values.stream().collect(Collectors.toMap(Widget::getId,v->v));
        for(var p:requested){var widget=byId.get(p.widgetId());if(p.size()==null||!registry.get(widget.getType(),widget.getConfigVersion()).sizes().contains(p.size()))throw WidgetException.invalid("size");}
        if(d.isPresent())d.get().check(revision);else if(revision!=0)throw WidgetException.conflict();capacity(w);
        var now=projectClock.instant();if(d.isPresent())d.get().advance(revision,now);else layouts.insert(HomeLayout.create(w.getId(),now));
        var old=layouts.placements(w.getId()).stream().collect(Collectors.toMap(WidgetPlacement::getWidgetId,WidgetPlacement::getId));
        var next=new ArrayList<WidgetPlacement>();for(var p:requested)next.add(WidgetPlacement.create(old.getOrDefault(p.widgetId(),UUID.randomUUID()),w.getId(),p.widgetId(),next.size(),p.size()));
        layouts.replacePlacements(w.getId(),next);w.recordBusinessMutation();layouts.flush();return queries.get(user);
    }
    public WidgetCreationResult initialize(UUID user,String key,String hash) {
        var w=workspace(user);var now=projectClock.instant().truncatedTo(ChronoUnit.MICROS);
        var cached=replay.find(w.getId(),WidgetReplay.INITIALIZE,key,hash,now);if(cached.isPresent())return cached.get();
        if(layouts.find(w.getId(),true).isPresent())throw WidgetException.conflict();capacity(w);
        layouts.insert(HomeLayout.create(w.getId(),now));var p=new ArrayList<WidgetPlacement>();
        for(var legacy:HomeDashboard.defaults()) {
            var c=new LocalWidgetConfig(new WidgetSelection("all",null,null),null);String json=WidgetJson.MAPPER.writeValueAsString(c);
            registry.get(legacy.type(),1).decode(json);
            var widget=Widget.create(w.getId(),legacy.type(),legacy.title(),1,json,now);widgets.insert(widget);
            p.add(WidgetPlacement.create(UUID.randomUUID(),w.getId(),widget.getId(),p.size(),legacy.size()));
        }
        layouts.replacePlacements(w.getId(),p);w.recordBusinessMutation();layouts.flush();
        return replay.save(w.getId(),WidgetReplay.INITIALIZE,key,hash,queries.get(user),"/api/v3/dashboards/home",now,w.getDataRevision());
    }
    private PersonalWorkspace workspace(UUID user){var w=workspaces.lockByOwnerId(user).orElseThrow(WidgetException::missing);users.resolve(user);return w;}
    private void capacity(PersonalWorkspace w){if(w.getDataRevision()==Long.MAX_VALUE)throw WidgetException.conflict();}
}
