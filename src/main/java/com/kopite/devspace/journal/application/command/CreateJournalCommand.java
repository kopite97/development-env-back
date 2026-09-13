package com.kopite.devspace.journal.application.command;
import com.kopite.devspace.journal.domain.JournalValues;
import java.util.UUID;
public record CreateJournalCommand(String title,UUID projectId,String body,String entryDate) {
    public JournalValues values() { return new JournalValues(title,projectId,body,JournalValues.date(entryDate,"entryDate")); }
}
