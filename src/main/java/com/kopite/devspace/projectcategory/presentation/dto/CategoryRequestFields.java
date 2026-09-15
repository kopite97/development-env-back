package com.kopite.devspace.projectcategory.presentation.dto;
import com.kopite.devspace.projectcategory.domain.CategoryValidationException;
import tools.jackson.core.*;
import java.util.*;
public final class CategoryRequestFields {
    private CategoryRequestFields() {}
    static Map<String,Object> read(JsonParser parser,boolean patch) {
        if(parser.currentToken()!=JsonToken.START_OBJECT) throw invalid("body");
        Map<String,Object> fields=new HashMap<>();
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(parser.currentToken()!=JsonToken.PROPERTY_NAME) throw invalid("body");
            String field=parser.currentName();var token=parser.nextToken();
            if(fields.containsKey(field)) throw invalid(field);
            if(field.equals("name") && token==JsonToken.VALUE_STRING) fields.put(field,parser.getString());
            else if(patch && field.equals("revision") && token==JsonToken.VALUE_NUMBER_INT) fields.put(field,parser.getLongValue());
            else throw invalid(field);
        }
        return fields;
    }
    public static UUID uuid(String value) {
        try {UUID id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw invalid("id");return id;}
        catch(IllegalArgumentException ex){throw invalid("id");}
    }
    public static CategoryValidationException invalid(String field) {return new CategoryValidationException(field,"invalid, missing, duplicate or non-writable field");}
}
