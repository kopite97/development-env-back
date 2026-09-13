package com.kopite.devspace.link.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="link_collections")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class LinkCollection {
    @Id private UUID workspaceId;
    @Column(nullable=false) private long revision;
    public static LinkCollection empty(UUID workspace) {
        LinkCollection result = new LinkCollection();
        result.workspaceId=Objects.requireNonNull(workspace); return result;
    }
    public void checkRevision(long expected) {
        if (expected < 0 || expected > Link.MAX_REVISION)
            throw new LinkValidationException("collectionRevision", "must be a nonnegative safe integer");
        if (expected != revision) throw new LinkConflictException("REVISION_CONFLICT");
    }
    public void checkCanAdvance() {
        if (revision == Link.MAX_REVISION) throw new LinkConflictException("REVISION_CONFLICT");
    }
    public void advance() { checkCanAdvance(); revision++; }
}
