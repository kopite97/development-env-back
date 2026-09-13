package com.kopite.devspace.task.application.model;
import com.kopite.devspace.task.application.exception.TaskNotFoundException;
import com.kopite.devspace.task.domain.Task;
import com.kopite.devspace.project.domain.Project;
import java.time.Instant;
import java.util.UUID;
public record TaskSnapshot(UUID id, long revision, Instant createdAt, Instant updatedAt, String title,
    UUID projectId, String projectName, String scope, String description, String status, String priority,
    String tag, Instant deletedAt) {
    public static TaskSnapshot from(Task task, Project project) {
        if (!task.getWorkspaceId().equals(project.getWorkspaceId()) || !task.getProjectId().equals(project.getId()))
            throw new TaskNotFoundException();
        return new TaskSnapshot(task.getId(), task.getRevision(), task.getCreatedAt(), task.getUpdatedAt(),
            task.getTitle(), project.getId(), project.getName(), project.getScope(), task.getDescription(),
            task.getStatus(), task.getPriority(), task.getTag(), task.getDeletedAt());
    }
}
