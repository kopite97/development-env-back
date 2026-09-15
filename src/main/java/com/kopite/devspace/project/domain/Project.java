package com.kopite.devspace.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
    public static final long MAX_REVISION = 9007199254740991L;

    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID workspaceId;
    private UUID categoryId;
    @Column(nullable = false, columnDefinition = "text")
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    private String subtitle;
    @Column(nullable = false, columnDefinition = "text")
    private String stack;
    @Column(nullable = false, columnDefinition = "numeric")
    private BigDecimal progress;
    @Column(nullable = false, columnDefinition = "text")
    private String currentMilestone;
    @Column(nullable = false, columnDefinition = "text")
    private String repositoryUrl;
    @Column(nullable = false, columnDefinition = "text")
    private String status;
    @Column(nullable = false)
    private long revision;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public static Project create(UUID workspaceId, ProjectValues values, Instant now) {
        Project project = new Project();
        project.id = UUID.randomUUID();
        project.workspaceId = Objects.requireNonNull(workspaceId);
        project.assign(values);
        project.status = "active";
        project.revision = 1;
        project.createdAt = Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
        project.updatedAt = project.createdAt;
        return project;
    }

    public void checkRevision(long expected) {
        if (expected < 1 || expected > MAX_REVISION) {
            throw new ProjectValidationException("revision", "must be a positive safe integer");
        }
        if (revision != expected) throw new ProjectRevisionConflictException();
    }

    public void update(long expected, ProjectValues values, String status, Instant now) {
        checkRevision(expected);
        if (!"active".equals(status) && !"archived".equals(status)) {
            throw new ProjectValidationException("status", "must be active or archived");
        }
        if (revision == MAX_REVISION) throw new ProjectRevisionConflictException();
        Instant timestamp = Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
        assign(values);
        this.status = status;
        revision++;
        updatedAt = timestamp;
    }

    public ProjectValues values() {
        return new ProjectValues(name, subtitle, stack, progress, currentMilestone, repositoryUrl, categoryId);
    }


    private void assign(ProjectValues values) {
        Objects.requireNonNull(values);
        name = values.name();
        categoryId = values.categoryId();
        subtitle = values.subtitle();
        stack = values.stack();
        progress = values.progress();
        currentMilestone = values.currentMilestone();
        repositoryUrl = values.repositoryUrl();
    }
}
