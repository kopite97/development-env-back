package com.kopite.devspace.journal.application.model;
import com.kopite.devspace.journal.application.exception.JournalNotFoundException;
import com.kopite.devspace.journal.domain.Journal;
import com.kopite.devspace.project.domain.Project;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record JournalSnapshot(UUID id,long revision,Instant createdAt,Instant updatedAt,String title,
    UUID projectId,String projectName,String scope,String body,LocalDate entryDate) {
    public static JournalSnapshot from(Journal journal,Project project) {
        if(!journal.getWorkspaceId().equals(project.getWorkspaceId()) || !journal.getProjectId().equals(project.getId()))
            throw new JournalNotFoundException();
        return new JournalSnapshot(journal.getId(),journal.getRevision(),journal.getCreatedAt(),journal.getUpdatedAt(),
            journal.getTitle(),project.getId(),project.getName(),project.getScope(),journal.getBody(),journal.getEntryDate());
    }
}
