package com.kopite.devspace.task.presentation.dto;
import com.kopite.devspace.task.application.query.TaskQueryService;
import java.time.Instant;
public record TaskStatsResponse(Counts counts,long total,Instant asOf) {
    public record Counts(long todo,long doing,long done) {}
    public static TaskStatsResponse from(TaskQueryService.Stats stats) {
        return new TaskStatsResponse(new Counts(stats.counts().get("todo"),stats.counts().get("doing"),
            stats.counts().get("done")),stats.total(),stats.asOf());
    }
}
