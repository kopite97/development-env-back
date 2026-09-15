package com.kopite.devspace.task.infrastructure.persistence;
import com.kopite.devspace.task.application.model.TaskCursor;
import com.kopite.devspace.task.application.model.TaskSnapshot;
import com.kopite.devspace.task.application.port.TaskSearchRepository;
import com.kopite.devspace.task.application.query.TaskListFilter;
import com.kopite.devspace.task.domain.Task;
import com.kopite.devspace.project.domain.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class TaskSearchAdapter implements TaskSearchRepository {
    private final EntityManager entityManager;
    private static final String FROM = " from Task t join Project p on p.id=t.projectId and p.workspaceId=t.workspaceId";
    public Optional<TaskSnapshot> findOwned(UUID workspace, UUID id) {
        return entityManager.createQuery("select t as task,p as project" + FROM + " where t.workspaceId=:workspace and t.id=:id", Tuple.class)
            .setParameter("workspace", workspace).setParameter("id", id).getResultStream().map(this::snapshot).findFirst();
    }
    public long count(UUID workspace, TaskListFilter filter) {
        return bind(entityManager.createQuery("select count(t)" + FROM + where(filter), Long.class), workspace, filter).getSingleResult();
    }
    public List<TaskSnapshot> page(UUID workspace, TaskListFilter filter, TaskCursor after) {
        String seek = after == null ? "" : " and (t.createdAt < :createdAt or (t.createdAt=:createdAt and t.id < :id))";
        var query = bind(entityManager.createQuery("select t as task,p as project" + FROM + where(filter) + seek
            + " order by t.createdAt desc,t.id desc", Tuple.class), workspace, filter);
        if (after != null) query.setParameter("createdAt", after.createdAt()).setParameter("id", after.id());
        return query.setMaxResults(filter.limit()+1).getResultList().stream().map(this::snapshot).toList();
    }
    public Map<String,Long> counts(UUID workspace, TaskListFilter filter) {
        Map<String,Long> counts = new LinkedHashMap<>();
        counts.put("todo",0L); counts.put("doing",0L); counts.put("done",0L);
        bind(entityManager.createQuery("select t.status as status,count(t) as count" + FROM + where(filter)
            + " group by t.status", Tuple.class), workspace, filter).getResultList()
            .forEach(row -> counts.put(row.get("status",String.class),row.get("count",Long.class)));
        return counts;
    }
    private TaskSnapshot snapshot(Tuple row) {
        return TaskSnapshot.from(row.get("task",Task.class),row.get("project",Project.class));
    }
    private String where(TaskListFilter f) {
        return " where t.workspaceId=:workspace"
            + (f.projectId()==null ? "" : " and t.projectId=:projectId")
            + ("all".equals(f.category()) ? "" : ("uncategorized".equals(f.category()) ? " and p.categoryId is null" : " and p.categoryId=:categoryId"))
            + ("all".equals(f.projectStatus()) ? "" : " and p.status=:projectStatus")
            + (f.status()==null ? "" : " and t.status=:status")
            + (f.deleted() ? " and t.deletedAt is not null" : " and t.deletedAt is null")
            + " and (lower(t.title) like lower(:pattern) escape '!' or lower(p.name) like lower(:pattern) escape '!')";
    }
    private <T> TypedQuery<T> bind(TypedQuery<T> query, UUID workspace, TaskListFilter f) {
        query.setParameter("workspace",workspace);
        if(f.projectId()!=null) query.setParameter("projectId",f.projectId());
        if(com.kopite.devspace.projectcategory.application.CategoryFilter.id(f.category())!=null) query.setParameter("categoryId",com.kopite.devspace.projectcategory.application.CategoryFilter.id(f.category()));
        if(!"all".equals(f.projectStatus())) query.setParameter("projectStatus",f.projectStatus());
        if(f.status()!=null) query.setParameter("status",f.status());
        return query.setParameter("pattern","%"+f.query().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
    }
}
