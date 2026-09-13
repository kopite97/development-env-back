package com.kopite.devspace.task.application.command;
import com.kopite.devspace.task.domain.TaskValues;
import java.util.UUID;
public record CreateTaskCommand(String title, UUID projectId, String description, String status, String priority, String tag) {
    public TaskValues values() {
        return new TaskValues(title, projectId, description == null ? "" : description,
            status == null ? "todo" : status, priority == null ? "normal" : priority, tag == null ? "" : tag);
    }
}
