package com.kopite.devspace.link.infrastructure.persistence;
import com.kopite.devspace.link.domain.Link;
import com.kopite.devspace.link.domain.LinkRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class LinkRepositoryAdapter implements LinkRepository {
    private final EntityManager em;
    public Link save(Link link) { em.persist(link); return link; }
    public void delete(Link link) { em.remove(link); }
    public Optional<Link> findOwned(UUID workspace, UUID id) {
        return em.createQuery("from Link l where l.workspaceId=:workspace and l.id=:id",Link.class)
            .setParameter("workspace",workspace).setParameter("id",id).getResultStream().findFirst();
    }
    public Optional<Link> lockOwned(UUID workspace, UUID id) {
        return em.createQuery("from Link l where l.workspaceId=:workspace and l.id=:id",Link.class)
            .setParameter("workspace",workspace).setParameter("id",id).setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultStream().findFirst();
    }
    public List<Link> lockAll(UUID workspace) {
        return em.createQuery("from Link l where l.workspaceId=:workspace order by l.id",Link.class)
            .setParameter("workspace",workspace).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }
    public long count(UUID workspace) {
        return em.createQuery("select count(l) from Link l where l.workspaceId=:workspace",Long.class).setParameter("workspace",workspace).getSingleResult();
    }
    public long maxPosition(UUID workspace) {
        return em.createQuery("select coalesce(max(l.position),-1) from Link l where l.workspaceId=:workspace",Long.class).setParameter("workspace",workspace).getSingleResult();
    }
    public void beginReorder() { em.createNativeQuery("SET CONSTRAINTS uq_links_workspace_position DEFERRED").executeUpdate(); }
    public void finishReorder() {
        em.flush();
        em.createNativeQuery("SET CONSTRAINTS uq_links_workspace_position IMMEDIATE").executeUpdate();
    }
}
