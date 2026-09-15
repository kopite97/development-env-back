package com.kopite.devspace.link.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Link {
    public static final long MAX_REVISION = 9007199254740991L;
    @Id private UUID id;
    @Column(nullable=false, updatable=false) private UUID workspaceId;
    @Column(nullable=false, columnDefinition="text") private String label;
    @Column(nullable=false, columnDefinition="text") private String description;
    @Column(nullable=false, columnDefinition="text") private String url;
    @Column private UUID projectId;
    @Column(nullable=false) private long position;
    @Column(nullable=false) private long revision;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;

    public static Link create(UUID workspace, LinkValues values, long position, Instant now) {
        checkPosition(position);
        Link link = new Link();
        link.id = UUID.randomUUID(); link.workspaceId = Objects.requireNonNull(workspace);
        link.assign(values); link.position = position; link.revision = 1;
        link.createdAt = Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
        link.updatedAt = link.createdAt;
        return link;
    }
    public void checkRevision(long expected) {
        if (expected < 1 || expected > MAX_REVISION) throw new LinkValidationException("revision", "must be a positive safe integer");
        if (expected != revision) throw new LinkConflictException("REVISION_CONFLICT");
    }
    public void checkCanAdvance() {
        if (revision == MAX_REVISION) throw new LinkConflictException("REVISION_CONFLICT");
    }
    public void update(long expected, LinkValues values, Instant now) {
        checkRevision(expected); checkCanAdvance();
        Instant time = Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
        assign(values); revision++; updatedAt = time;
    }
    public void move(long position, Instant now) {
        checkPosition(position);
        if (this.position == position) return;
        checkCanAdvance();
        Instant time = Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
        this.position = position; revision++; updatedAt = time;
    }
    public LinkValues values() { return new LinkValues(label, description, url, projectId); }
    public static long nextPosition(long max) {
        if (max >= MAX_REVISION) throw new LinkConflictException("REVISION_CONFLICT");
        return max + 1;
    }
    private static void checkPosition(long value) {
        if (value < 0 || value > MAX_REVISION) throw new LinkValidationException("position", "must be a nonnegative safe integer");
    }
    private void assign(LinkValues values) {
        Objects.requireNonNull(values);
        label=values.label(); description=values.description(); url=values.url(); projectId=values.projectId();
    }
}
