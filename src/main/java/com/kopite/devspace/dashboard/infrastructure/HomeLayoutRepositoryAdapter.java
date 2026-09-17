package com.kopite.devspace.dashboard.infrastructure;
import com.kopite.devspace.dashboard.domain.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository @RequiredArgsConstructor
public class HomeLayoutRepositoryAdapter implements HomeLayoutRepository {
    private final EntityManager em;
    public Optional<HomeLayout> find(UUID w,boolean lock) {
        return Optional.ofNullable(lock?em.find(HomeLayout.class,new HomeLayout.Key(w,"home"),LockModeType.PESSIMISTIC_WRITE):em.find(HomeLayout.class,new HomeLayout.Key(w,"home")));
    }
    public List<WidgetPlacement> placements(UUID w) {
        return em.createQuery("from WidgetPlacement p where p.workspaceId=:w and p.dashboardKey='home' order by p.position",WidgetPlacement.class).setParameter("w",w).getResultList();
    }
    public void insert(HomeLayout d){em.persist(d);}
    public void replacePlacements(UUID w,List<WidgetPlacement> values) {
        // Flush deletes before reinserting the retained identities: avoids position uniqueness swaps.
        for(var p:placements(w))em.remove(p);
        em.flush();
        for(var p:values)em.persist(p);
    }
    public void flush(){em.flush();}
}
