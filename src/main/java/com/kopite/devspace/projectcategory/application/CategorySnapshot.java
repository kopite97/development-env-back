package com.kopite.devspace.projectcategory.application;
import com.kopite.devspace.projectcategory.domain.ProjectCategory;
import java.time.Instant;
import java.util.UUID;
public record CategorySnapshot(UUID id,String name,long revision,Instant createdAt,Instant updatedAt, @com.fasterxml.jackson.annotation.JsonIgnore Long dataRevision) {
    public CategorySnapshot(UUID id,String name,long revision,Instant createdAt,Instant updatedAt) { this(id, name, revision, createdAt, updatedAt, null); }
    public CategorySnapshot observed(long workspaceRevision) { return new CategorySnapshot(id, name, revision, createdAt, updatedAt, workspaceRevision); }
    public static CategorySnapshot from(ProjectCategory c) {
        return new CategorySnapshot(c.getId(),c.getName(),c.getRevision(),c.getCreatedAt(),c.getUpdatedAt());
    }
}
