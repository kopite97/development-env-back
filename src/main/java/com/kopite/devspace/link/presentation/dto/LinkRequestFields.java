package com.kopite.devspace.link.presentation.dto;
import com.kopite.devspace.link.domain.LinkValidationException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import java.util.*;

public final class LinkRequestFields {
    private LinkRequestFields() {}
    static Map<String,String> read(JsonParser parser,boolean patch) {
        if(parser.currentToken()!=JsonToken.START_OBJECT) throw invalid("body");
        Map<String,String> fields=new HashMap<>();
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(parser.currentToken()!=JsonToken.PROPERTY_NAME) throw invalid("body");
            String name=parser.currentName();var token=parser.nextToken();
            if(fields.containsKey(name)) throw invalid(name);
            if(Set.of("label","description","url","scope").contains(name)) {
                if(token!=JsonToken.VALUE_STRING) throw invalid(name);
                fields.put(name,parser.getString());
            } else if(patch&&name.equals("revision")) {
                if(token!=JsonToken.VALUE_NUMBER_INT) throw invalid(name);
                fields.put(name,Long.toString(parser.getLongValue()));
            } else throw invalid(name);
        }
        return fields;
    }
    public static UUID uuid(String value,String field) {
        try {UUID id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw invalid(field);return id;}
        catch(IllegalArgumentException|NullPointerException ex){throw invalid(field);}
    }
    public static LinkValidationException invalid(String field) {return new LinkValidationException(field,"invalid, null, duplicate or non-writable field");}
}
