package com.kopite.devspace.dashboard.application;
import com.kopite.devspace.dashboard.domain.*;
import java.util.*;
public record HomeDashboardSnapshot(long revision,List<DashboardWidget> widgets,Set<String> missingCategoryWidgetIds,Long dataRevision) {
    public HomeDashboardSnapshot {widgets=List.copyOf(widgets);missingCategoryWidgetIds=Set.copyOf(missingCategoryWidgetIds);}
    public HomeDashboardSnapshot(long revision,List<DashboardWidget> widgets){this(revision,widgets,Set.of(),null);}
    public HomeDashboardSnapshot observed(Set<String> missing,long workspaceRevision){return new HomeDashboardSnapshot(revision,widgets,missing,workspaceRevision);}
    public static HomeDashboardSnapshot from(HomeDashboard d){d.validateStored();return new HomeDashboardSnapshot(d.getRevision(),d.getWidgets());}
}
