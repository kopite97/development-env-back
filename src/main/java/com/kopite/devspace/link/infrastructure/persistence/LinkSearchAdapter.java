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
        if(!filter.scope().equals("all")) where+=" and (l.scope='all' or l.scope=:scope)";
        if(!filter.query().isEmpty()) where+=" and (lower(l.label) like lower(:query) escape '!' or lower(l.description) like lower(:query) escape '!' or lower(l.url) like lower(:query) escape '!')";
        var query=em.createQuery("from Link l"+where+" order by l.position,l.id",Link.class).setParameter("workspace",workspace);
        if(!filter.scope().equals("all")) query.setParameter("scope",filter.scope());
        if(!filter.query().isEmpty()) query.setParameter("query","%"+filter.query().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        return query.getResultList().stream().map(LinkSnapshot::from).toList();
    }
}
