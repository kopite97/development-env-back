package com.kopite.devspace.widget.application;
import com.kopite.devspace.widget.domain.*;
import com.kopite.devspace.project.domain.ProjectRepository;
import com.kopite.devspace.projectcategory.domain.ProjectCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class WidgetReferences {
    private final ProjectRepository projects;
    private final ProjectCategoryRepository categories;
    private final WidgetTypeRegistry registry;
    public List<WidgetSnapshot> snapshots(UUID workspace,List<Widget> values,Long revision) {
        var configs=new HashMap<UUID,WidgetConfig>();var selections=new HashMap<UUID,WidgetSelection>();
        for(var w:values){var d=registry.get(w.getType(),w.getConfigVersion());var c=d.decode(w.getConfig());configs.put(w.getId(),c);selections.put(w.getId(),d.selection(c));}
        var projectIds=selections.values().stream().filter(Objects::nonNull).map(WidgetSelection::projectId).filter(Objects::nonNull).collect(Collectors.toSet());
        if(projects.findOwnedByIds(workspace,projectIds).size()!=projectIds.size())throw WidgetException.missing();
        var categoryIds=selections.values().stream().filter(Objects::nonNull).map(WidgetSelection::categoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        var owned=categories.findOwnedByIds(workspace,categoryIds).stream().map(c->c.getId()).collect(Collectors.toSet());
        return values.stream().map(w->{var s=selections.get(w.getId());return WidgetSnapshot.from(w,configs.get(w.getId()),s!=null&&s.categoryId()!=null&&!owned.contains(s.categoryId()),revision);}).toList();
    }
    public void validate(UUID workspace,WidgetSelection value,WidgetSelection previous) {
        if(value==null)return;
        if(value.projectId()!=null&&projects.findOwned(workspace,value.projectId()).isEmpty())throw WidgetException.missing();
        if(value.categoryId()!=null&&categories.findOwned(workspace,value.categoryId()).isEmpty()&&!value.equals(previous))throw WidgetException.missing();
    }
}
