CREATE TABLE projects (
    id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    name TEXT NOT NULL,
    subtitle TEXT NOT NULL DEFAULT '',
    scope TEXT NOT NULL,
    stack TEXT NOT NULL,
    progress NUMERIC NOT NULL DEFAULT 0,
    current_milestone TEXT NOT NULL DEFAULT '',
    repository_url TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'active',
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT uq_projects_workspace_id UNIQUE (workspace_id, id),
    CONSTRAINT fk_projects_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT,
    CONSTRAINT ck_projects_name CHECK (length(btrim(name)) > 0 AND length(name) <= 100),
    CONSTRAINT ck_projects_subtitle CHECK (length(subtitle) <= 4000),
    CONSTRAINT ck_projects_scope CHECK (scope IN ('unity', 'server')),
    CONSTRAINT ck_projects_stack CHECK (length(btrim(stack)) > 0 AND length(stack) <= 200),
    CONSTRAINT ck_projects_progress CHECK (progress >= 0 AND progress <= 100),
    CONSTRAINT ck_projects_milestone CHECK (length(current_milestone) <= 200),
    CONSTRAINT ck_projects_repository_url CHECK (length(repository_url) <= 2000),
    CONSTRAINT ck_projects_status CHECK (status IN ('active', 'archived')),
    CONSTRAINT ck_projects_revision CHECK (revision BETWEEN 1 AND 9007199254740991)
);

CREATE INDEX ix_projects_workspace_created ON projects(workspace_id, created_at DESC, id DESC);
CREATE INDEX ix_projects_workspace_status_created ON projects(workspace_id, status, created_at DESC, id DESC);
