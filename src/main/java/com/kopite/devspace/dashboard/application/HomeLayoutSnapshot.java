package com.kopite.devspace.dashboard.application;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kopite.devspace.widget.application.WidgetSnapshot;
import java.util.*;
public record HomeLayoutSnapshot(String id,int schemaVersion,boolean initialized,long layoutRevision,List<Placement> placements,List<WidgetSnapshot> widgets,@JsonIgnore Long dataRevision) {
    public record Placement(UUID id,UUID widgetId,String size){}
    public HomeLayoutSnapshot {placements=List.copyOf(placements);widgets=List.copyOf(widgets);}
}
