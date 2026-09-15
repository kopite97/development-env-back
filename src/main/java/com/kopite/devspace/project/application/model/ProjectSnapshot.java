package com.kopite.devspace.project.application.model;

import com.kopite.devspace.project.domain.Project;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProjectSnapshot(UUID id, long revision, Instant createdAt, Instant updatedAt,
                              String name, String subtitle, String stack, BigDecimal progress,
                              String currentMilestone, String repositoryUrl, String status, UUID categoryId, @com.fasterxml.jackson.annotation.JsonIgnore Long dataRevision) {
    public ProjectSnapshot(UUID id, long revision, Instant createdAt, Instant updatedAt,
                              String name, String subtitle, String stack, BigDecimal progress,
                              String currentMilestone, String repositoryUrl, String status, UUID categoryId) { this(id, revision, createdAt, updatedAt, name, subtitle, stack, progress, currentMilestone, repositoryUrl, status, categoryId, null); }
    public ProjectSnapshot observed(long workspaceRevision) { return new ProjectSnapshot(id, revision, createdAt, updatedAt, name, subtitle, stack, progress, currentMilestone, repositoryUrl, status, categoryId, workspaceRevision); }
    public static ProjectSnapshot from(Project project) {
        return new ProjectSnapshot(project.getId(), project.getRevision(), project.getCreatedAt(), project.getUpdatedAt(),
                project.getName(), project.getSubtitle(), project.getStack(), project.getProgress(),
                project.getCurrentMilestone(), project.getRepositoryUrl(), project.getStatus(), project.getCategoryId());
    }
}
