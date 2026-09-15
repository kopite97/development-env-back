package com.kopite.devspace.project.infrastructure.persistence;
import com.kopite.devspace.project.application.model.ProjectCursor;
import com.kopite.devspace.project.application.port.ProjectSearchRepository;
import com.kopite.devspace.project.application.query.ProjectListFilter;

import com.kopite.devspace.project.domain.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProjectSearchAdapter implements ProjectSearchRepository {
    private final EntityManager entityManager;

    @Override public long count(UUID workspaceId, ProjectListFilter filter) {
        return bind(entityManager.createQuery("select count(p) from Project p" + where(filter), Long.class), workspaceId, filter).getSingleResult();
    }

    @Override public List<Project> page(UUID workspaceId, ProjectListFilter filter, ProjectCursor after) {
        String clause = after == null ? "" : " and (p.createdAt < :createdAt or (p.createdAt = :createdAt and p.id < :id))";
        var query = bind(entityManager.createQuery("select p from Project p" + where(filter) + clause
                + " order by p.createdAt desc, p.id desc", Project.class), workspaceId, filter);
        if (after != null) query.setParameter("createdAt", after.createdAt()).setParameter("id", after.id());
        return query.setMaxResults(filter.limit() + 1).getResultList();
    }

    private String where(ProjectListFilter filter) {
        return " where p.workspaceId = :workspaceId"
                + (filter.category().equals("all") ? "" : ("uncategorized".equals(filter.category()) ? " and p.categoryId is null" : " and p.categoryId=:categoryId"))
                + (filter.status().equals("all") ? "" : " and p.status = :status")
                + " and (lower(p.name) like lower(:pattern) escape '!' or lower(p.stack) like lower(:pattern) escape '!')";
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query, UUID workspaceId, ProjectListFilter filter) {
        query.setParameter("workspaceId", workspaceId);
        if(com.kopite.devspace.projectcategory.application.CategoryFilter.id(filter.category())!=null) query.setParameter("categoryId",com.kopite.devspace.projectcategory.application.CategoryFilter.id(filter.category()));
        if (!filter.status().equals("all")) query.setParameter("status", filter.status());
        return query.setParameter("pattern", "%" + filter.query().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }
}
