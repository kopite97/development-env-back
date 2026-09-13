package com.kopite.devspace.dashboard.application;
import com.kopite.devspace.dashboard.domain.*;
import java.util.List;
public record HomeDashboardSnapshot(long revision,List<DashboardWidget> widgets) {
    public HomeDashboardSnapshot { widgets=List.copyOf(widgets); }
    public static HomeDashboardSnapshot from(HomeDashboard d) {
        d.validateStored(); return new HomeDashboardSnapshot(d.getRevision(),d.getWidgets());
    }
}
