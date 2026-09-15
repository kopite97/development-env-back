package com.kopite.devspace.dashboard.application;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.project.domain.ProjectRepository;
import com.kopite.devspace.projectcategory.domain.ProjectCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Batch validation within the caller's read snapshot or workspace-locked mutation. */
@Service @RequiredArgsConstructor
public class DashboardReferences {
    private final ProjectRepository projects;
    private final ProjectCategoryRepository categories;
    public Set<String> missingCategories(UUID workspace,List<DashboardWidget> widgets) {
        var projectIds=widgets.stream().map(w->w.selection().projectId()).filter(Objects::nonNull).collect(Collectors.toSet());
        if(projects.findOwnedByIds(workspace,projectIds).size()!=projectIds.size())throw new DashboardNotFoundException();
        var categoryIds=widgets.stream().map(w->w.selection().categoryId()).filter(Objects::nonNull).collect(Collectors.toSet());
        var owned=categories.findOwnedByIds(workspace,categoryIds).stream().map(c->c.getId()).collect(Collectors.toSet());
        return widgets.stream().filter(w->w.selection().categoryId()!=null && !owned.contains(w.selection().categoryId()))
            .map(DashboardWidget::id).collect(Collectors.toUnmodifiableSet());
    }
    public Set<String> validateSave(UUID workspace,List<DashboardWidget> values,List<DashboardWidget> previous) {
        var missing=missingCategories(workspace,values);
        var old=previous.stream().collect(Collectors.toMap(DashboardWidget::id,DashboardWidget::selection));
        for(var widget:values)if(missing.contains(widget.id()) && !widget.selection().equals(old.get(widget.id())))throw new DashboardNotFoundException();
        return missing;
    }
}
