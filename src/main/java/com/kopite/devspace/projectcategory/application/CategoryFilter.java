package com.kopite.devspace.projectcategory.application;
import com.kopite.devspace.projectcategory.domain.CategoryValidationException;
import java.util.UUID;

/** Query selector only; UUID relation fields never accept sentinel values. */
public final class CategoryFilter {
    private CategoryFilter() {}
    public static String normalize(String value) {
        if("all".equals(value)||"uncategorized".equals(value))return value;
        try {
            UUID id=UUID.fromString(value);
            if(!id.toString().equalsIgnoreCase(value))throw new IllegalArgumentException();
            return id.toString();
        } catch(IllegalArgumentException|NullPointerException e){throw new CategoryValidationException("category","must be all, uncategorized or a UUID");}
    }
    public static UUID id(String value){return "all".equals(value)||"uncategorized".equals(value)?null:UUID.fromString(value);}
}
