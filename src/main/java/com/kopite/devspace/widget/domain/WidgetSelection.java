package com.kopite.devspace.widget.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WidgetSelection(String kind,UUID projectId,UUID categoryId) {
    public WidgetSelection {
        if(kind==null || !switch(kind) {
            case "all","uncategorized" -> projectId==null&&categoryId==null;
            case "project" -> projectId!=null&&categoryId==null;
            case "category" -> projectId==null&&categoryId!=null;
            default -> false;
        })throw WidgetException.invalid("selection");
    }
    public String categoryFilter(){return categoryId!=null?categoryId.toString():"uncategorized".equals(kind)?kind:"all";}
}
