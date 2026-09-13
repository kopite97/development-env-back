package com.kopite.devspace.task.application.command;
import com.kopite.devspace.task.domain.TaskValues;
import java.util.UUID;
public record UpdateTaskCommand(long revision, String title, UUID projectId, String description, String status, String priority, String tag) {
    public TaskValues applyTo(TaskValues old) {
        return new TaskValues(title == null ? old.title() : title, projectId == null ? old.projectId() : projectId,
            description == null ? old.description() : description, status == null ? old.status() : status,
            priority == null ? old.priority() : priority, tag == null ? old.tag() : tag);
    }
}
