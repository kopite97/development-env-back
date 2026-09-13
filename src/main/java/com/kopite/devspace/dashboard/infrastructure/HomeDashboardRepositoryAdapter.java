package com.kopite.devspace.dashboard.infrastructure;
import com.kopite.devspace.dashboard.domain.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
@RequiredArgsConstructor
public class HomeDashboardRepositoryAdapter implements HomeDashboardRepository {
    private final EntityManager em;
    public Optional<HomeDashboard> find(UUID workspace) {
        return Optional.ofNullable(em.find(HomeDashboard.class,new HomeDashboard.Key(workspace,"home")));
    }
    public Optional<HomeDashboard> lock(UUID workspace) {
        return Optional.ofNullable(em.find(HomeDashboard.class,new HomeDashboard.Key(workspace,"home"),LockModeType.PESSIMISTIC_WRITE));
    }
    public void insert(HomeDashboard dashboard) { em.persist(dashboard); }
    public void flush() { em.flush(); }
}
