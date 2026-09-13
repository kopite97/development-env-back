package com.kopite.devspace.journal.presentation.dto;
import com.kopite.devspace.journal.domain.JournalValidationException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import java.util.*;

public final class JournalRequestFields {
    private static final Set<String> STRINGS=Set.of("title","projectId","body","entryDate");
    private JournalRequestFields() {}
    static Map<String,Object> read(JsonParser parser, boolean patch) {
        if(parser.currentToken()!=JsonToken.START_OBJECT) throw invalid("body");
        Map<String,Object> fields=new HashMap<>();
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(parser.currentToken()!=JsonToken.PROPERTY_NAME) throw invalid("body");
            String name=parser.currentName();
            JsonToken token=parser.nextToken();
            if(fields.containsKey(name)) throw invalid(name);
            if(STRINGS.contains(name)) {
                if(token!=JsonToken.VALUE_STRING) throw invalid(name);
                fields.put(name,parser.getString());
            } else if(patch && name.equals("revision")) {
                if(token!=JsonToken.VALUE_NUMBER_INT) throw invalid(name);
                fields.put(name,parser.getLongValue());
            } else throw invalid(name);
        }
        return fields;
    }
    static String text(Map<String,Object> fields,String key) { return (String) fields.get(key); }
    public static UUID uuid(String value,String field) {
        if(value==null) return null;
        try {
            UUID id=UUID.fromString(value);
            if(!id.toString().equalsIgnoreCase(value)) throw invalid(field);
            return id;
        } catch(IllegalArgumentException ex) { throw invalid(field); }
    }
    private static JournalValidationException invalid(String field) {
        return new JournalValidationException(field,"invalid, null, duplicate or non-writable field");
    }
}
