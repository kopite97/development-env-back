package com.kopite.devspace.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "display_name", nullable = false, columnDefinition = "text")
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    private User(UUID id, String displayName, Instant createdAt) {
        this.id = id;
        this.displayName = normalizeDisplayName(displayName);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        this.disabledAt = null;
    }

    public static User create(String displayName) {
        return new User(UUID.randomUUID(), displayName, Instant.now());
    }

    private static String normalizeDisplayName(String displayName) {
        String normalized = Objects.requireNonNull(displayName, "displayName").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return normalized;
    }

}
