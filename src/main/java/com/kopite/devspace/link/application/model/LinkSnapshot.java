package com.kopite.devspace.link.application.model;
import com.kopite.devspace.link.domain.Link;
import java.time.Instant;
import java.util.UUID;
public record LinkSnapshot(UUID id,long revision,Instant createdAt,Instant updatedAt,String label,String description,String url,String scope,long position) {
    public static LinkSnapshot from(Link l) { return new LinkSnapshot(l.getId(),l.getRevision(),l.getCreatedAt(),l.getUpdatedAt(),l.getLabel(),l.getDescription(),l.getUrl(),l.getScope(),l.getPosition()); }
}
