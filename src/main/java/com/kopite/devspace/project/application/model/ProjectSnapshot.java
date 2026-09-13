package com.kopite.devspace.project.application.model;

import com.kopite.devspace.project.domain.Project;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProjectSnapshot(UUID id, long revision, Instant createdAt, Instant updatedAt,
                              String name, String subtitle, String scope, String stack, BigDecimal progress,
                              String currentMilestone, String repositoryUrl, String status, String colorToken) {
    public static ProjectSnapshot from(Project project) {
        return new ProjectSnapshot(project.getId(), project.getRevision(), project.getCreatedAt(), project.getUpdatedAt(),
                project.getName(), project.getSubtitle(), project.getScope(), project.getStack(), project.getProgress(),
                project.getCurrentMilestone(), project.getRepositoryUrl(), project.getStatus(), project.colorToken());
    }
}
