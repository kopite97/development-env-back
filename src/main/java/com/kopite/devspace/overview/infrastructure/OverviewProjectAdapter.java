package com.kopite.devspace.overview.infrastructure;
import com.kopite.devspace.overview.application.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
@RequiredArgsConstructor
public class OverviewProjectAdapter implements OverviewProjectRepository {
    private final EntityManager em;
    public List<Count> counts(UUID workspace,OverviewFilter filter) {
        String where=" where p.workspaceId=:workspace"
            + ("all".equals(filter.scope())?"":" and p.scope=:scope")
            + (filter.projectId()==null?"":" and p.id=:projectId");
        var query=em.createQuery("select p.scope as scope,p.status as status,count(p) as count from Project p"+where+" group by p.scope,p.status",Tuple.class)
            .setParameter("workspace",workspace);
        if(!"all".equals(filter.scope()))query.setParameter("scope",filter.scope());
        if(filter.projectId()!=null)query.setParameter("projectId",filter.projectId());
        return query.getResultList().stream().map(r->new Count(r.get("scope",String.class),r.get("status",String.class),r.get("count",Long.class))).toList();
    }
}
