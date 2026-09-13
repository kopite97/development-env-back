package com.kopite.devspace.dashboard.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardWidget(String id, String type, String title, String scope, String size, UUID projectId, Integer limit) {
    private static final Set<String> TYPES=Set.of("overview","board","deploy","links","journal","milestone");
    public DashboardWidget {
        id=text(id,"id",Integer.MAX_VALUE);
        title=text(title,"title",48);
        if(type==null || !TYPES.contains(type)) throw invalid("type");
        if(scope==null || !Set.of("all","unity","server").contains(scope)) throw invalid("scope");
        if(size==null || !Set.of("small","medium","wide").contains(size)) throw invalid("size");
        if(Set.of("deploy","links").contains(type) && (projectId!=null || limit!=null)) throw invalid("settings");
        if(limit!=null && (limit<1 || limit>20)) throw invalid("limit");
        if(projectId!=null) scope="all";
    }
    private static String text(String value,String field,int max) {
        if(value==null || value.trim().isBlank() || value.trim().length()>max) throw invalid(field);
        return value.trim();
    }
    private static DashboardValidationException invalid(String field) {
        return new DashboardValidationException(field,"invalid widget field");
    }
}
