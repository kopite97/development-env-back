package com.kopite.devspace.overview.application;
import com.kopite.devspace.task.application.query.TaskQueryService;
import java.util.List;
public record OverviewSnapshot(OverviewFilter filter,List<OverviewProjectRepository.Count> projects,TaskQueryService.Stats tasks) {
    public OverviewSnapshot {projects=List.copyOf(projects);}
}
