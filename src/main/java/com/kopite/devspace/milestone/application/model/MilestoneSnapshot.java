package com.kopite.devspace.milestone.application.model;
import com.kopite.devspace.milestone.application.exception.MilestoneNotFoundException;
import com.kopite.devspace.milestone.domain.Milestone;
import com.kopite.devspace.project.domain.Project;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record MilestoneSnapshot(UUID id,long revision,Instant createdAt,Instant updatedAt,String title,
    UUID projectId,String projectName,String scope,LocalDate dueDate,boolean completed) {
    public static MilestoneSnapshot from(Milestone milestone,Project project) {
        if(!milestone.getWorkspaceId().equals(project.getWorkspaceId()) || !milestone.getProjectId().equals(project.getId()))
            throw new MilestoneNotFoundException();
        return new MilestoneSnapshot(milestone.getId(),milestone.getRevision(),milestone.getCreatedAt(),milestone.getUpdatedAt(),
            milestone.getTitle(),project.getId(),project.getName(),project.getScope(),milestone.getDueDate(),milestone.isCompleted());
    }
}
