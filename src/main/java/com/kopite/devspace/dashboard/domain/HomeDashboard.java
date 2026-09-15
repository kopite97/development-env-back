package com.kopite.devspace.dashboard.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Entity
@Table(name="dashboards")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class HomeDashboard {
    public static final long MAX_REVISION=9007199254740991L;
    @Embeddable
    public record Key(UUID workspaceId,String dashboardKey) implements Serializable {}
    @EmbeddedId private Key id;
    @Column(nullable=false) private int schemaVersion;
    @Column(nullable=false) private long revision;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable=false,columnDefinition="jsonb")
    private List<DashboardWidget> widgets;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;

    public static HomeDashboard create(UUID workspace,List<DashboardWidget> widgets,Instant now) {
        HomeDashboard result=new HomeDashboard();
        result.id=new Key(Objects.requireNonNull(workspace),"home");
        result.schemaVersion=2; result.revision=1; result.widgets=validate(widgets);
        result.createdAt=Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
        result.updatedAt=result.createdAt;
        return result;
    }
    public List<DashboardWidget> getWidgets() { return List.copyOf(widgets); }
    public static List<DashboardWidget> validate(List<DashboardWidget> widgets) {
        if(widgets==null) throw new DashboardValidationException("widgets","required array");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<widgets.size();i++) {
            DashboardWidget widget=widgets.get(i);
            if(widget==null) throw new DashboardValidationException("widgets["+i+"]","must not be null");
            if(!ids.add(widget.id())) throw new DashboardValidationException("widgets["+i+"].id","must be unique within dashboard");
        }
        return List.copyOf(widgets);
    }
    public static void validateRevision(long revision) {
        if(revision<0 || revision>MAX_REVISION) throw new DashboardValidationException("revision","must be a nonnegative safe integer");
    }
    public void replace(long expected,List<DashboardWidget> values,Instant now) {
        validateRevision(expected);
        if(expected!=revision || revision==MAX_REVISION) throw new DashboardConflictException();
        widgets=validate(values); revision++; updatedAt=now.truncatedTo(ChronoUnit.MICROS);
    }
    public void validateStored() {
        if(schemaVersion!=2 || revision<1 || revision>MAX_REVISION) throw new IllegalStateException("Invalid stored dashboard");
        try { validate(widgets); } catch(DashboardValidationException ex) { throw new IllegalStateException("Invalid stored dashboard",ex); }
    }
    public static List<DashboardWidget> defaults() {
        return List.of(
            new DashboardWidget("home-overview","overview","프로젝트 개요","wide",DashboardSelection.all(),null),
            new DashboardWidget("home-board","board","작업 보드","wide",DashboardSelection.all(),null),
            new DashboardWidget("home-deploy","deploy","운영","medium",DashboardSelection.all(),null),
            new DashboardWidget("home-links","links","바로가기","small",DashboardSelection.all(),null),
            new DashboardWidget("home-journal","journal","개발 일지","medium",DashboardSelection.all(),null),
            new DashboardWidget("home-milestone","milestone","마일스톤","medium",DashboardSelection.all(),null));
    }
}
