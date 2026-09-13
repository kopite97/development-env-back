package com.kopite.devspace.project.application.port;
import com.kopite.devspace.project.application.model.ProjectCursor;
import com.kopite.devspace.project.application.query.ProjectListFilter;

import com.kopite.devspace.project.domain.Project;
import java.util.List;
import java.util.UUID;

public interface ProjectSearchRepository {
    long count(UUID workspaceId, ProjectListFilter filter);
    List<Project> page(UUID workspaceId, ProjectListFilter filter, ProjectCursor after);
}
