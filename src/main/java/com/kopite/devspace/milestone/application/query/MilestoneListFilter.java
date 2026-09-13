package com.kopite.devspace.milestone.application.query;
import com.kopite.devspace.milestone.domain.MilestoneValidationException;
import java.util.UUID;
public record MilestoneListFilter(String scope,UUID projectId,String projectStatus,String status,int limit) {
    public MilestoneListFilter {
        if(!"all".equals(scope)&&!"unity".equals(scope)&&!"server".equals(scope)) invalid("scope");
        if(!"all".equals(projectStatus)&&!"active".equals(projectStatus)&&!"archived".equals(projectStatus)) invalid("projectStatus");
        if(!"all".equals(status)&&!"open".equals(status)&&!"done".equals(status)) invalid("status");
        if(limit<1||limit>100) invalid("limit");
    }
    private static void invalid(String field) {throw new MilestoneValidationException(field,"invalid filter value");}
}
