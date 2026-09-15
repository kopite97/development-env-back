package com.kopite.devspace.dashboard.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardWidget(String id, String type, String title, String size, DashboardSelection selection, Integer limit) {
    private static final Set<String> TYPES=Set.of("overview","board","deploy","links","journal","milestone");
    public DashboardWidget {
        id=text(id,"id",Integer.MAX_VALUE);
        title=text(title,"title",48);
        if(type==null || !TYPES.contains(type)) throw invalid("type");
        if(selection==null) throw invalid("selection");
        if(size==null || !Set.of("small","medium","wide").contains(size)) throw invalid("size");
        if(Set.of("deploy","links").contains(type) && limit!=null) throw invalid("limit");
        if(type.equals("deploy") && !selection.kind().equals("all")) throw invalid("selection");
        if(limit!=null && (limit<1 || limit>20)) throw invalid("limit");
    }
    private static String text(String value,String field,int max) {
        if(value==null || value.trim().isBlank() || value.trim().length()>max) throw invalid(field);
        return value.trim();
    }
    private static DashboardValidationException invalid(String field) {
        return new DashboardValidationException(field,"invalid widget field");
    }
}
