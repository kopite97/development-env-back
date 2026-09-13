package com.kopite.devspace.project.application.port;
import com.kopite.devspace.project.application.model.ProjectCursor;
import com.kopite.devspace.project.application.query.ProjectListFilter;

import java.util.UUID;

public interface ProjectCursorCodec {
    String encode(UUID workspaceId, ProjectListFilter filter, ProjectCursor position);
    ProjectCursor decode(UUID workspaceId, ProjectListFilter filter, String cursor);
}
