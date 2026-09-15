package com.kopite.devspace.link.infrastructure.persistence;
import com.kopite.devspace.link.application.model.LinkSnapshot;
import com.kopite.devspace.link.application.port.LinkSearchRepository;
import com.kopite.devspace.link.application.query.LinkListFilter;
import com.kopite.devspace.link.domain.Link;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class LinkSearchAdapter implements LinkSearchRepository {
    private final EntityManager em;
    public List<LinkSnapshot> list(UUID workspace,LinkListFilter filter) {
        String where=" where l.workspaceId=:workspace";
        if(!filter.category().equals("all"))where+="uncategorized".equals(filter.category())?" and p.categoryId is null":" and p.categoryId=:categoryId";
        if(filter.projectId()!=null)where+=" and l.projectId=:projectId";
        if(!filter.projectStatus().equals("all"))where+=" and p.status=:projectStatus";
        if(!filter.query().isEmpty()) where+=" and (lower(l.label) like lower(:query) escape '!' or lower(l.description) like lower(:query) escape '!' or lower(l.url) like lower(:query) escape '!')";
        var query=em.createQuery("select l as link,p as project from Link l left join Project p on p.workspaceId=l.workspaceId and p.id=l.projectId"+where+" order by l.position,l.id",jakarta.persistence.Tuple.class).setParameter("workspace",workspace);
        var categoryId=com.kopite.devspace.projectcategory.application.CategoryFilter.id(filter.category());
        if(categoryId!=null)query.setParameter("categoryId",categoryId);
        if(filter.projectId()!=null)query.setParameter("projectId",filter.projectId());
        if(!filter.projectStatus().equals("all"))query.setParameter("projectStatus",filter.projectStatus());
        if(!filter.query().isEmpty()) query.setParameter("query","%"+filter.query().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        return query.getResultList().stream().map(row->LinkSnapshot.from(row.get("link",Link.class),row.get("project",com.kopite.devspace.project.domain.Project.class))).toList();
    }
}
