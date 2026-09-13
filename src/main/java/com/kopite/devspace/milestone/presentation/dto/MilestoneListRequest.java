package com.kopite.devspace.milestone.presentation.dto;
import com.kopite.devspace.milestone.application.query.MilestoneListFilter;
public record MilestoneListRequest(String scope,String projectId,String projectStatus,String status,int limit) {
    public MilestoneListFilter filter() { return new MilestoneListFilter(scope,MilestoneRequestFields.uuid(projectId,"projectId"),projectStatus,status,limit); }
}
