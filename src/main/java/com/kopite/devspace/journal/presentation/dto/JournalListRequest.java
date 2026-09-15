package com.kopite.devspace.journal.presentation.dto;
import com.kopite.devspace.journal.application.query.JournalListFilter;
import com.kopite.devspace.journal.domain.JournalValues;
public record JournalListRequest(String category,String projectId,String projectStatus,String query,String from,String to,String sort,int limit) {
    public JournalListFilter filter() {
        return new JournalListFilter(category,JournalRequestFields.uuid(projectId,"projectId"),projectStatus,query,
            from==null?null:JournalValues.date(from,"from"),to==null?null:JournalValues.date(to,"to"),sort,limit);
    }
}
