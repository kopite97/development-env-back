package com.kopite.devspace.task.domain;

import java.util.UUID;

/** Complete editable values; request omission is resolved by the application. */
public record TaskValues(String title, UUID projectId, String description, String status,
                         String priority, String tag) {
    public TaskValues {
        title = text("title", title, 160, true, true);
        if (projectId == null) throw new TaskValidationException("projectId", "must not be null");
        description = text("description", description, 10000, false, false);
        tag = text("tag", tag, 40, false, true);
        if (!"todo".equals(status) && !"doing".equals(status) && !"done".equals(status))
            throw new TaskValidationException("status", "must be todo, doing or done");
        if (!"normal".equals(priority) && !"high".equals(priority))
            throw new TaskValidationException("priority", "must be normal or high");
    }

    private static String text(String field, String value, int max, boolean required, boolean trim) {
        if (value == null) throw new TaskValidationException(field, "must not be null");
        String result = trim ? value.trim() : value;
        if (required && result.isBlank()) throw new TaskValidationException(field, "must not be blank");
        if (result.length() > max)
            throw new TaskValidationException(field, "must be at most " + max + " UTF-16 code units");
        return result;
    }
}
