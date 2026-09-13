package com.kopite.devspace.project.presentation.dto;

import com.kopite.devspace.project.domain.ProjectValidationException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class ProjectRequestFields {
    private static final Set<String> STRINGS = Set.of("name", "subtitle", "scope", "stack", "currentMilestone", "repositoryUrl");
    private ProjectRequestFields() {}

    static Map<String, Object> read(JsonParser parser, boolean patch) {
        if (parser.currentToken() != JsonToken.START_OBJECT) throw invalid("body", "must be an object");
        Map<String, Object> fields = new HashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) throw invalid("body", "invalid object");
            String field = parser.currentName();
            JsonToken token = parser.nextToken();
            if (fields.containsKey(field)) throw invalid(field, "must not be repeated");
            if (STRINGS.contains(field) || (patch && field.equals("status"))) {
                if (token != JsonToken.VALUE_STRING) throw invalid(field, "must be a non-null string");
                fields.put(field, parser.getString());
            } else if (field.equals("progress")) {
                if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) throw invalid(field, "must be a number");
                fields.put(field, parser.getDecimalValue());
            } else if (patch && field.equals("revision")) {
                if (token != JsonToken.VALUE_NUMBER_INT) throw invalid(field, "must be an integer");
                fields.put(field, parser.getLongValue());
            } else {
                throw invalid(field, "is not writable");
            }
        }
        return fields;
    }

    static String text(Map<String, Object> fields, String name) { return (String) fields.get(name); }
    static BigDecimal progress(Map<String, Object> fields) { return (BigDecimal) fields.get("progress"); }
    private static ProjectValidationException invalid(String field, String message) { return new ProjectValidationException(field, message); }
}
