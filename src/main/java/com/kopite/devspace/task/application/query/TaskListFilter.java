package com.kopite.devspace.task.application.query;
import com.kopite.devspace.task.domain.TaskValidationException;
import java.util.UUID;
public record TaskListFilter(String scope, UUID projectId, String projectStatus, String query,
                             String status, boolean deleted, int limit) {
    public TaskListFilter {
        if (!"all".equals(scope) && !"unity".equals(scope) && !"server".equals(scope)) invalid("scope");
        if (!"all".equals(projectStatus) && !"active".equals(projectStatus) && !"archived".equals(projectStatus)) invalid("projectStatus");
        if (status != null && !"todo".equals(status) && !"doing".equals(status) && !"done".equals(status)) invalid("status");
        if (query == null) invalid("query");
        if (limit < 1 || limit > 100) invalid("limit");
    }
    private static void invalid(String field) { throw new TaskValidationException(field, "invalid filter value"); }
}
