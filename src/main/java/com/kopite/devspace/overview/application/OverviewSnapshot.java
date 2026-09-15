package com.kopite.devspace.overview.application;
import com.kopite.devspace.task.application.query.TaskQueryService;
import java.util.List;
public record OverviewSnapshot(OverviewFilter filter,List<ProjectCategoryCount> projects,TaskQueryService.Stats tasks) {
    public Long dataRevision() { return tasks.dataRevision(); }
    public OverviewSnapshot {projects=List.copyOf(projects);}
}
