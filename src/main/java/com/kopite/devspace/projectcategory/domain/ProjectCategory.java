package com.kopite.devspace.projectcategory.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Entity
@Table(name = "project_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCategory {
    public static final long MAX_REVISION = 9007199254740991L;
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID workspaceId;
    @Column(nullable = false, columnDefinition = "text") private String name;
    @Column(nullable = false) private long revision;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public static ProjectCategory create(UUID workspace, CategoryName name, Instant now) {
        var category = new ProjectCategory();
        category.id = UUID.randomUUID();
        category.workspaceId = Objects.requireNonNull(workspace);
        category.name = Objects.requireNonNull(name).value();
        category.revision = 1;
        category.createdAt = Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
        category.updatedAt = category.createdAt;
        return category;
    }
    public static void validateRevision(long revision) {
        if (revision < 1 || revision > MAX_REVISION)
            throw new CategoryValidationException("revision", "must be a positive safe integer");
    }
    public void checkRevision(long expected) {
        validateRevision(expected);
        if (revision != expected) throw new CategoryConflictException("REVISION_CONFLICT");
    }
    public void rename(long expected, CategoryName name, Instant now) {
        checkRevision(expected);
        if (revision == MAX_REVISION) throw new CategoryConflictException("REVISION_CONFLICT");
        var timestamp = Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
        this.name = Objects.requireNonNull(name).value();
        revision++;
        updatedAt = timestamp;
    }
}
