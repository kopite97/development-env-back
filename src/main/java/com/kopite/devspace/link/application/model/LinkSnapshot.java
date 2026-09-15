package com.kopite.devspace.link.application.model;
import com.kopite.devspace.link.domain.Link;
import java.time.Instant;
import java.util.UUID;
public record LinkSnapshot(UUID id,long revision,Instant createdAt,Instant updatedAt,String label,String description,String url,
    UUID projectId,String projectName,UUID categoryId,long position, @com.fasterxml.jackson.annotation.JsonIgnore Long dataRevision) {
    public LinkSnapshot(UUID id,long revision,Instant createdAt,Instant updatedAt,String label,String description,String url,
    UUID projectId,String projectName,UUID categoryId,long position) {this(id, revision, createdAt, updatedAt, label, description, url, projectId, projectName, categoryId, position,null);}
    public LinkSnapshot observed(long workspaceRevision) {return new LinkSnapshot(id, revision, createdAt, updatedAt, label, description, url, projectId, projectName, categoryId, position,workspaceRevision);}
    public static LinkSnapshot from(Link l,com.kopite.devspace.project.domain.Project p) {
        if(l.getProjectId()!=null&&(p==null||!l.getProjectId().equals(p.getId())||!l.getWorkspaceId().equals(p.getWorkspaceId())))
            throw new com.kopite.devspace.link.application.exception.LinkNotFoundException();
        return new LinkSnapshot(l.getId(),l.getRevision(),l.getCreatedAt(),l.getUpdatedAt(),l.getLabel(),l.getDescription(),
            l.getUrl(),l.getProjectId(),p==null?null:p.getName(),p==null?null:p.getCategoryId(),l.getPosition());
    }
}
