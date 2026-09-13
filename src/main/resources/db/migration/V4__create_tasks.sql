CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    project_id UUID NOT NULL,
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0 AND length(title) <= 160),
    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 10000),
    status TEXT NOT NULL DEFAULT 'todo' CHECK (status IN ('todo','doing','done')),
    priority TEXT NOT NULL DEFAULT 'normal' CHECK (priority IN ('normal','high')),
    tag TEXT NOT NULL DEFAULT '' CHECK (length(tag) <= 40),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision BETWEEN 1 AND 9007199254740991),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_tasks_owned_project FOREIGN KEY (workspace_id,project_id)
        REFERENCES projects(workspace_id,id) ON DELETE RESTRICT
);
CREATE INDEX ix_tasks_workspace_created ON tasks(workspace_id,created_at DESC,id DESC);
CREATE INDEX ix_tasks_workspace_project_created ON tasks(workspace_id,project_id,created_at DESC,id DESC);
CREATE INDEX ix_tasks_workspace_status_live ON tasks(workspace_id,status,created_at DESC,id DESC)
    WHERE deleted_at IS NULL;
