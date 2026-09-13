package com.kopite.devspace.task.presentation.dto;
import com.kopite.devspace.task.application.query.TaskListFilter;
public record TaskStatsRequest(String scope,String projectId,String projectStatus,String query) {
    public TaskListFilter filter() {
        return new TaskListFilter(scope,TaskRequestFields.uuid(projectId,"projectId"),projectStatus,query,null,false,20);
    }
}
