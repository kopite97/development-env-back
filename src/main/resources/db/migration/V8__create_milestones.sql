CREATE TABLE milestones (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    project_id UUID NOT NULL,
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0 AND length(title) <= 200),
    due_date DATE CHECK (due_date IS NULL OR due_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    completed BOOLEAN NOT NULL DEFAULT false,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision BETWEEN 1 AND 9007199254740991),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_milestones_owned_project FOREIGN KEY (workspace_id,project_id)
        REFERENCES projects(workspace_id,id) ON DELETE RESTRICT
);
-- SQL guards complement Java nonblank and UTF-16 validation.
CREATE INDEX ix_milestones_workspace_order ON milestones(workspace_id,completed,due_date ASC NULLS LAST,id);
CREATE INDEX ix_milestones_workspace_project_order ON milestones(workspace_id,project_id,completed,due_date ASC NULLS LAST,id);
