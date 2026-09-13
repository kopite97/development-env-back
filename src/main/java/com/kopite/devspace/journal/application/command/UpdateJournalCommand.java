package com.kopite.devspace.journal.application.command;
import com.kopite.devspace.journal.domain.JournalValues;
import java.util.UUID;
public record UpdateJournalCommand(long revision,String title,UUID projectId,String body,String entryDate) {
    public JournalValues applyTo(JournalValues old) {
        return new JournalValues(title==null?old.title():title,projectId==null?old.projectId():projectId,
            body==null?old.body():body,entryDate==null?old.entryDate():JournalValues.date(entryDate,"entryDate"));
    }
}
