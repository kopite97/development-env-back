package com.kopite.devspace.dashboard.domain;
import com.kopite.devspace.widget.domain.WidgetException;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name="dashboard_widget_placements") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class WidgetPlacement {
    @Id private UUID id;
    @Column(nullable=false) private UUID workspaceId;
    @Column(nullable=false) private String dashboardKey;
    @Column(nullable=false) private UUID widgetId;
    @Column(nullable=false) private int position;
    @Column(nullable=false) private String size;
    public static WidgetPlacement create(UUID id,UUID workspace,UUID widget,int position,String size) {
        if(position<0||size==null||!Set.of("small","medium","wide").contains(size))throw WidgetException.invalid("placements");
        var p=new WidgetPlacement();p.id=Objects.requireNonNull(id);p.workspaceId=Objects.requireNonNull(workspace);
        p.dashboardKey="home";p.widgetId=Objects.requireNonNull(widget);p.position=position;p.size=size;return p;
    }
}
