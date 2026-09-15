package com.kopite.devspace.projectcategory.domain;

import java.util.*;

public interface ProjectCategoryRepository {
    Optional<ProjectCategory> findOwned(UUID workspace, UUID id);
    Optional<ProjectCategory> lockOwned(UUID workspace, UUID id);
    List<ProjectCategory> list(UUID workspace);
    List<ProjectCategory> findOwnedByIds(UUID workspace,Set<UUID> ids);
    long count(UUID workspace);
    boolean nameExists(UUID workspace, String name, UUID excluding);
    boolean inUse(UUID workspace, UUID id);
    ProjectCategory save(ProjectCategory category);
    void delete(ProjectCategory category);
    void flush();
}
