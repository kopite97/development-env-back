package com.kopite.devspace.link.application.query;
import com.kopite.devspace.link.domain.LinkValidationException;
import com.kopite.devspace.projectcategory.application.CategoryFilter;
import java.util.UUID;
public record LinkListFilter(String category,UUID projectId,String projectStatus,String query) {
    public LinkListFilter(String category,String query) {this(category,null,"all",query);}
    public LinkListFilter {
        category=CategoryFilter.normalize(category);
        if(!java.util.Set.of("all","active","archived").contains(projectStatus))throw new LinkValidationException("projectStatus","invalid status");
        if(query==null) query="";
    }
}
