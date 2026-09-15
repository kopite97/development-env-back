CREATE TABLE project_categories (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    name TEXT COLLATE "C" NOT NULL CHECK (length(btrim(name)) > 0 AND length(name) <= 100),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision BETWEEN 1 AND 9007199254740991),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_project_categories_workspace_id UNIQUE (workspace_id,id),
    CONSTRAINT uq_project_categories_workspace_name UNIQUE (workspace_id,name)
);
ALTER TABLE projects ADD COLUMN category_id UUID;
ALTER TABLE projects ADD CONSTRAINT fk_projects_owned_category
    FOREIGN KEY (workspace_id,category_id) REFERENCES project_categories(workspace_id,id) ON DELETE RESTRICT;
CREATE INDEX ix_projects_workspace_category ON projects(workspace_id,category_id);
