package com.kopite.devspace.milestone.infrastructure.persistence;
import com.kopite.devspace.milestone.application.model.MilestoneCursor;
import com.kopite.devspace.milestone.application.model.MilestoneSnapshot;
import com.kopite.devspace.milestone.application.port.MilestoneSearchRepository;
import com.kopite.devspace.milestone.application.query.MilestoneListFilter;
import com.kopite.devspace.milestone.domain.Milestone;
import com.kopite.devspace.project.domain.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class MilestoneSearchAdapter implements MilestoneSearchRepository {
    private final EntityManager entityManager;
    private static final String FROM = " from Milestone t join Project p on p.id=t.projectId and p.workspaceId=t.workspaceId";
    public Optional<MilestoneSnapshot> findOwned(UUID workspace, UUID id) {
        return entityManager.createQuery("select t as milestone,p as project" + FROM + " where t.workspaceId=:workspace and t.id=:id", Tuple.class)
            .setParameter("workspace", workspace).setParameter("id", id).getResultStream().map(this::snapshot).findFirst();
    }
    public long count(UUID workspace, MilestoneListFilter filter) {
        return bind(entityManager.createQuery("select count(t)" + FROM + where(filter), Long.class), workspace, filter).getSingleResult();
    }
    public List<MilestoneSnapshot> page(UUID workspace, MilestoneListFilter filter, MilestoneCursor after) {
        String seek="";
        if(after!=null) {
            String withinDate=after.dueDate()==null?"t.dueDate is null and t.id>:id"
                :"t.dueDate is null or t.dueDate>:dueDate or (t.dueDate=:dueDate and t.id>:id)";
            seek=" and ((t.completed=true and :afterCompleted=false) or (t.completed=:afterCompleted and ("+withinDate+")))";
        }
        var query=bind(entityManager.createQuery("select t as milestone,p as project"+FROM+where(filter)+seek
            +" order by t.completed asc,t.dueDate asc nulls last,t.id asc",Tuple.class),workspace,filter);
        if(after!=null) {
            query.setParameter("afterCompleted",after.completed()).setParameter("id",after.id());
            if(after.dueDate()!=null) query.setParameter("dueDate",after.dueDate());
        }
        return query.setMaxResults(filter.limit()+1).getResultList().stream().map(this::snapshot).toList();
    }
    private MilestoneSnapshot snapshot(Tuple row) {
        return MilestoneSnapshot.from(row.get("milestone",Milestone.class),row.get("project",Project.class));
    }
    private String where(MilestoneListFilter f) {
        return " where t.workspaceId=:workspace"
            + (f.projectId()==null ? "" : " and t.projectId=:projectId")
            + ("all".equals(f.scope()) ? "" : " and p.scope=:scope")
            + ("all".equals(f.projectStatus()) ? "" : " and p.status=:projectStatus")
            + ("all".equals(f.status()) ? "" : " and t.completed=:completed");
    }
    private <T> TypedQuery<T> bind(TypedQuery<T> query, UUID workspace, MilestoneListFilter f) {
        query.setParameter("workspace",workspace);
        if(f.projectId()!=null) query.setParameter("projectId",f.projectId());
        if(!"all".equals(f.scope())) query.setParameter("scope",f.scope());
        if(!"all".equals(f.projectStatus())) query.setParameter("projectStatus",f.projectStatus());
        if(!"all".equals(f.status())) query.setParameter("completed","done".equals(f.status()));
        return query;
    }
}
