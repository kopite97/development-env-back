package com.kopite.devspace.journal.infrastructure.persistence;
import com.kopite.devspace.journal.application.model.JournalCursor;
import com.kopite.devspace.journal.application.model.JournalSnapshot;
import com.kopite.devspace.journal.application.port.JournalSearchRepository;
import com.kopite.devspace.journal.application.query.JournalListFilter;
import com.kopite.devspace.journal.domain.Journal;
import com.kopite.devspace.project.domain.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class JournalSearchAdapter implements JournalSearchRepository {
    private final EntityManager entityManager;
    private static final String FROM = " from Journal t join Project p on p.id=t.projectId and p.workspaceId=t.workspaceId";
    public Optional<JournalSnapshot> findOwned(UUID workspace, UUID id) {
        return entityManager.createQuery("select t as journal,p as project" + FROM + " where t.workspaceId=:workspace and t.id=:id", Tuple.class)
            .setParameter("workspace", workspace).setParameter("id", id).getResultStream().map(this::snapshot).findFirst();
    }
    public long count(UUID workspace, JournalListFilter filter) {
        return bind(entityManager.createQuery("select count(t)" + FROM + where(filter), Long.class), workspace, filter).getSingleResult();
    }
    public List<JournalSnapshot> page(UUID workspace, JournalListFilter filter, JournalCursor after) {
        String op = "newest".equals(filter.sort()) ? " < " : " > ";
        String direction = "newest".equals(filter.sort()) ? " desc" : " asc";
        String seek = after == null ? "" : " and (t.entryDate"+op+":entryDate or (t.entryDate=:entryDate and (t.createdAt"+op+":createdAt or (t.createdAt=:createdAt and t.id"+op+":id))))";
        var query = bind(entityManager.createQuery("select t as journal,p as project" + FROM + where(filter) + seek
            + " order by t.entryDate"+direction+",t.createdAt"+direction+",t.id"+direction, Tuple.class), workspace, filter);
        if (after != null) query.setParameter("entryDate",after.entryDate()).setParameter("createdAt", after.createdAt()).setParameter("id", after.id());
        return query.setMaxResults(filter.limit()+1).getResultList().stream().map(this::snapshot).toList();
    }
    private JournalSnapshot snapshot(Tuple row) {
        return JournalSnapshot.from(row.get("journal",Journal.class),row.get("project",Project.class));
    }
    private String where(JournalListFilter f) {
        return " where t.workspaceId=:workspace"
            + (f.projectId()==null ? "" : " and t.projectId=:projectId")
            + ("all".equals(f.scope()) ? "" : " and p.scope=:scope")
            + ("all".equals(f.projectStatus()) ? "" : " and p.status=:projectStatus")
            + (f.from()==null ? "" : " and t.entryDate>=:from")
            + (f.to()==null ? "" : " and t.entryDate<=:to")
            + " and (lower(t.body) like lower(:pattern) escape '!' or lower(t.title) like lower(:pattern) escape '!' or lower(p.name) like lower(:pattern) escape '!')";
    }
    private <T> TypedQuery<T> bind(TypedQuery<T> query, UUID workspace, JournalListFilter f) {
        query.setParameter("workspace",workspace);
        if(f.projectId()!=null) query.setParameter("projectId",f.projectId());
        if(!"all".equals(f.scope())) query.setParameter("scope",f.scope());
        if(!"all".equals(f.projectStatus())) query.setParameter("projectStatus",f.projectStatus());
        if(f.from()!=null) query.setParameter("from",f.from());
        if(f.to()!=null) query.setParameter("to",f.to());
        return query.setParameter("pattern","%"+f.query().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
    }
}
