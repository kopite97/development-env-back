package com.kopite.devspace.projectcategory.infrastructure;

import com.kopite.devspace.projectcategory.domain.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ProjectCategoryRepositoryAdapter implements ProjectCategoryRepository {
    private final EntityManager em;
    public Optional<ProjectCategory> findOwned(UUID workspace, UUID id) { return owned(workspace,id,false); }
    public Optional<ProjectCategory> lockOwned(UUID workspace, UUID id) { return owned(workspace,id,true); }
    private Optional<ProjectCategory> owned(UUID workspace, UUID id, boolean lock) {
        var q = em.createQuery("from ProjectCategory c where c.workspaceId=:w and c.id=:id",ProjectCategory.class)
            .setParameter("w",workspace).setParameter("id",id);
        if (lock) q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return q.getResultStream().findFirst();
    }
    public List<ProjectCategory> list(UUID workspace) {
        return em.createQuery("from ProjectCategory c where c.workspaceId=:w order by c.createdAt,c.id",ProjectCategory.class)
            .setParameter("w",workspace).getResultList();
    }
    public List<ProjectCategory> findOwnedByIds(UUID workspace,Set<UUID> ids) {
        if(ids.isEmpty())return List.of();
        return em.createQuery("from ProjectCategory c where c.workspaceId=:w and c.id in :ids",ProjectCategory.class)
            .setParameter("w",workspace).setParameter("ids",ids).getResultList();
    }
    public long count(UUID workspace) {
        return em.createQuery("select count(c) from ProjectCategory c where c.workspaceId=:w",Long.class).setParameter("w",workspace).getSingleResult();
    }
    public boolean nameExists(UUID workspace,String name,UUID excluding) {
        var q=em.createQuery("select count(c) from ProjectCategory c where c.workspaceId=:w and c.name=:name"
            +(excluding==null?"":" and c.id<>:id"),Long.class).setParameter("w",workspace).setParameter("name",name);
        if(excluding!=null) q.setParameter("id",excluding);
        return q.getSingleResult()>0;
    }
    public boolean inUse(UUID workspace,UUID id) {
        return !em.createQuery("select p.id from Project p where p.workspaceId=:w and p.categoryId=:id",UUID.class)
            .setParameter("w",workspace).setParameter("id",id).setMaxResults(1).getResultList().isEmpty();
    }
    public ProjectCategory save(ProjectCategory category) { em.persist(category); return category; }
    public void delete(ProjectCategory category) { em.remove(category); flush(); }
    public void flush() {
        try { em.flush(); }
        catch (RuntimeException ex) {
            for (Throwable cause=ex;cause!=null;cause=cause.getCause()) {
                if(cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                    if("uq_project_categories_workspace_name".equals(violation.getConstraintName()))
                        throw new CategoryConflictException("CATEGORY_NAME_CONFLICT");
                    if("fk_projects_owned_category".equals(violation.getConstraintName()))
                        throw new CategoryConflictException("CATEGORY_IN_USE");
                }
            }
            throw ex;
        }
    }
}
