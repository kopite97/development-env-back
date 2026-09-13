package com.kopite.devspace.journal.domain;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public record JournalValues(String title, UUID projectId, String body, LocalDate entryDate) {
    public JournalValues {
        title = text("title",title,120,true);
        body = text("body",body,20000,false);
        if(projectId==null) throw new JournalValidationException("projectId","must not be null");
        if(entryDate==null || entryDate.getYear()<1 || entryDate.getYear()>9999)
            throw new JournalValidationException("entryDate","must be a date in years 0001 through 9999");
    }
    public static LocalDate date(String value,String field) {
        if(value==null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new JournalValidationException(field,"must be YYYY-MM-DD");
        try {
            LocalDate date=LocalDate.parse(value);
            if(date.getYear()<1 || date.getYear()>9999) throw new DateTimeParseException("year",value,0);
            return date;
        } catch(DateTimeParseException ex) { throw new JournalValidationException(field,"must be a valid calendar date"); }
    }
    private static String text(String field,String value,int max,boolean trim) {
        if(value==null) throw new JournalValidationException(field,"must not be null");
        String result=trim?value.trim():value;
        if(result.isBlank()) throw new JournalValidationException(field,"must not be blank");
        if(result.length()>max) throw new JournalValidationException(field,"must be at most "+max+" UTF-16 code units");
        return result;
    }
}
