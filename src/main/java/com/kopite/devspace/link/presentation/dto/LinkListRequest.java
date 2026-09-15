package com.kopite.devspace.link.presentation.dto;
import com.kopite.devspace.link.application.query.LinkListFilter;
public record LinkListRequest(String category,String projectId,String projectStatus,String query) {
    public LinkListFilter filter(){return new LinkListFilter(category,projectId==null?null:LinkRequestFields.uuid(projectId,"projectId"),projectStatus,query);}
}
