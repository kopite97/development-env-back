package com.kopite.devspace.dashboard.domain;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardSelection(String kind, UUID projectId, UUID categoryId) {
    public DashboardSelection {
        boolean valid=kind!=null && switch(kind) {
            case "all","uncategorized" -> projectId==null && categoryId==null;
            case "project" -> projectId!=null && categoryId==null;
            case "category" -> projectId==null && categoryId!=null;
            default -> false;
        };
        if(!valid)throw new DashboardValidationException("selection","invalid selection shape");
    }
    public static DashboardSelection all(){return new DashboardSelection("all",null,null);}
}
