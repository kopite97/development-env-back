package com.kopite.devspace.link.application.command;

import com.kopite.devspace.link.domain.LinkValidationException;
import java.util.UUID;

/** Keeps transport presence and raw UUID spelling separate from relation identity. */
public record LinkProjectSelection(boolean present, String rawValue) {
    public LinkProjectSelection {
        if (!present && rawValue != null) throw new IllegalArgumentException("Absent Project has a value");
        if (rawValue != null) parse(rawValue);
    }
    public static LinkProjectSelection omitted() { return new LinkProjectSelection(false,null); }
    public UUID id() { return rawValue == null ? null : parse(rawValue); }
    private static UUID parse(String value) {
        try {
            UUID id=UUID.fromString(value);
            if(!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch(IllegalArgumentException ex) { throw new LinkValidationException("projectId","must be a UUID or null"); }
    }
}
