package com.kopite.devspace.journal.application.model;
import com.kopite.devspace.journal.application.exception.JournalNotFoundException;
import com.kopite.devspace.journal.domain.Journal;
import com.kopite.devspace.project.domain.Project;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record JournalSnapshot(UUID id,long revision,Instant createdAt,Instant updatedAt,String title,
    UUID projectId,String projectName,UUID categoryId,String body,LocalDate entryDate, @com.fasterxml.jackson.annotation.JsonIgnore Long dataRevision) {
    public JournalSnapshot(UUID id,long revision,Instant createdAt,Instant updatedAt,String title,
    UUID projectId,String projectName,UUID categoryId,String body,LocalDate entryDate) { this(id, revision, createdAt, updatedAt, title, projectId, projectName, categoryId, body, entryDate, null); }
    public JournalSnapshot observed(long workspaceRevision) { return new JournalSnapshot(id, revision, createdAt, updatedAt, title, projectId, projectName, categoryId, body, entryDate, workspaceRevision); }
    public static JournalSnapshot from(Journal journal,Project project) {
        if(!journal.getWorkspaceId().equals(project.getWorkspaceId()) || !journal.getProjectId().equals(project.getId()))
            throw new JournalNotFoundException();
        return new JournalSnapshot(journal.getId(),journal.getRevision(),journal.getCreatedAt(),journal.getUpdatedAt(),
            journal.getTitle(),project.getId(),project.getName(),project.getCategoryId(),journal.getBody(),journal.getEntryDate());
    }
}
