package com.kopite.devspace.workspace.domain;

import com.kopite.devspace.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalWorkspace {

    public static final String DEFAULT_NAME = "나의 작업실";

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_workspaces_owner")
    )
    private User owner;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "data_revision", nullable = false)
    private long dataRevision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private PersonalWorkspace(UUID id, User owner, String name, Instant createdAt) {
        this.id = id;
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = normalizeName(name);
        this.revision = 1;
        this.dataRevision = 0;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static PersonalWorkspace create(User owner) {
        return create(owner, DEFAULT_NAME);
    }

    public static PersonalWorkspace create(User owner, String name) {
        return new PersonalWorkspace(UUID.randomUUID(), owner, name, Instant.now());
    }

    private static String normalizeName(String name) {
        String normalized = Objects.requireNonNull(name, "name").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("name must be at most 100 UTF-16 code units");
        }
        return normalized;
    }

}
