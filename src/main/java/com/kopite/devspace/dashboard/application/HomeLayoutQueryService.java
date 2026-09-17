package com.kopite.devspace.dashboard.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.widget.domain.*;
import com.kopite.devspace.widget.application.WidgetReferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class HomeLayoutQueryService {
    private final CurrentUserService users;private final HomeLayoutRepository layouts;private final WidgetRepository widgets;private final WidgetReferences references;
    public HomeLayoutSnapshot get(UUID user) {
        var w=users.resolve(user).workspace();var d=layouts.find(w.getId(),false);var p=layouts.placements(w.getId());
        var ids=p.stream().map(WidgetPlacement::getWidgetId).collect(Collectors.toSet());var values=widgets.owned(w.getId(),ids);
        if(values.size()!=ids.size())throw WidgetException.missing();
        var byId=values.stream().collect(Collectors.toMap(Widget::getId,v->v));
        var ordered=p.stream().map(v->byId.get(v.getWidgetId())).toList();
        return new HomeLayoutSnapshot("home",3,d.isPresent(),d.map(HomeLayout::getLayoutRevision).orElse(0L),p.stream().map(v->new HomeLayoutSnapshot.Placement(v.getId(),v.getWidgetId(),v.getSize())).toList(),references.snapshots(w.getId(),ordered,w.getDataRevision()),w.getDataRevision());
    }
}
