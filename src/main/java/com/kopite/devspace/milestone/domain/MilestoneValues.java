package com.kopite.devspace.milestone.domain;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public record MilestoneValues(String title, UUID projectId, LocalDate dueDate, boolean completed) {
    public MilestoneValues {
        title = text("title",title,200,true);
        if(projectId==null) throw new MilestoneValidationException("projectId","must not be null");
        if(dueDate!=null && (dueDate.getYear()<1 || dueDate.getYear()>9999))
            throw new MilestoneValidationException("dueDate","must be a date in years 0001 through 9999");
    }
    public static LocalDate date(String value,String field) {
        if(value==null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new MilestoneValidationException(field,"must be YYYY-MM-DD");
        try {
            LocalDate date=LocalDate.parse(value);
            if(date.getYear()<1 || date.getYear()>9999) throw new DateTimeParseException("year",value,0);
            return date;
        } catch(DateTimeParseException ex) { throw new MilestoneValidationException(field,"must be a valid calendar date"); }
    }
    private static String text(String field,String value,int max,boolean trim) {
        if(value==null) throw new MilestoneValidationException(field,"must not be null");
        String result=trim?value.trim():value;
        if(result.isBlank()) throw new MilestoneValidationException(field,"must not be blank");
        if(result.length()>max) throw new MilestoneValidationException(field,"must be at most "+max+" UTF-16 code units");
        return result;
    }
}
