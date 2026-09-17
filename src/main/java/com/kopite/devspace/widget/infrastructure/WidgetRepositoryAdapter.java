package com.kopite.devspace.widget.infrastructure;
import com.kopite.devspace.widget.domain.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
@Repository @RequiredArgsConstructor
public class WidgetRepositoryAdapter implements WidgetRepository {
    private final EntityManager em;
    public Optional<Widget> owned(UUID w,UUID id,boolean lock) {
        var q=em.createQuery("from Widget w where w.workspaceId=:w and w.id=:id",Widget.class).setParameter("w",w).setParameter("id",id);
        if(lock)q.setLockMode(LockModeType.PESSIMISTIC_WRITE);return q.getResultStream().findFirst();
    }
    public List<Widget> owned(UUID w,Set<UUID> ids) {
        if(ids.isEmpty())return List.of();
        return em.createQuery("from Widget w where w.workspaceId=:w and w.id in :ids order by w.id",Widget.class).setParameter("w",w).setParameter("ids",ids).getResultList();
    }
    public List<Widget> page(UUID w,boolean unplaced,int limit,Instant at,UUID id) {
        String where="w.workspaceId=:w";
        if(unplaced)where+=" and not exists(select p.id from WidgetPlacement p where p.workspaceId=w.workspaceId and p.widgetId=w.id)";
        if(at!=null)where+=" and (w.createdAt<:at or (w.createdAt=:at and w.id<:id))";
        var q=em.createQuery("from Widget w where "+where+" order by w.createdAt desc,w.id desc",Widget.class).setParameter("w",w).setMaxResults(limit+1);
        if(at!=null)q.setParameter("at",at).setParameter("id",id);return q.getResultList();
    }
    public boolean placed(UUID w,UUID id) {
        return !em.createQuery("select p.id from WidgetPlacement p where p.workspaceId=:w and p.widgetId=:id",UUID.class).setParameter("w",w).setParameter("id",id).setMaxResults(1).getResultList().isEmpty();
    }
    public void insert(Widget w){em.persist(w);}
    public void delete(Widget w){em.remove(w);}
    public void flush(){em.flush();}
}
