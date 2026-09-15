package com.kopite.devspace.task.presentation.dto;
import com.kopite.devspace.task.application.query.TaskListFilter;
import com.kopite.devspace.task.domain.TaskValidationException;
public record TaskListRequest(String category,String projectId,String projectStatus,String query,String status,
                              String deleted,int limit,String cursor) {
    public TaskListFilter filter() {
        if(!"true".equals(deleted)&&!"false".equals(deleted)) throw new TaskValidationException("deleted","must be true or false");
        return new TaskListFilter(category,TaskRequestFields.uuid(projectId,"projectId"),projectStatus,query,status,
            Boolean.parseBoolean(deleted),limit);
    }
}
