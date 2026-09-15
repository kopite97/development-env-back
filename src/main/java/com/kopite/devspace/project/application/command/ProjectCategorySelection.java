package com.kopite.devspace.project.application.command;

import com.kopite.devspace.project.domain.ProjectValidationException;
import java.util.UUID;

/** Keeps transport presence and raw UUID spelling separate from relation identity. */
public record ProjectCategorySelection(boolean present, String rawValue) {
    public ProjectCategorySelection {
        if (!present && rawValue != null) throw new IllegalArgumentException("Absent Category has a value");
        if (rawValue != null) parse(rawValue);
    }
    public static ProjectCategorySelection omitted() { return new ProjectCategorySelection(false,null); }
    public UUID id() { return rawValue == null ? null : parse(rawValue); }
    private static UUID parse(String value) {
        try {
            UUID id=UUID.fromString(value);
            if(!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch(IllegalArgumentException ex) { throw new ProjectValidationException("categoryId","must be a UUID or null"); }
    }
}
