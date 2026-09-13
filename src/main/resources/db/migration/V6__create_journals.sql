CREATE TABLE journals (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    project_id UUID NOT NULL,
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0 AND length(title) <= 120),
    body TEXT NOT NULL CHECK (length(btrim(body)) > 0 AND length(body) <= 20000),
    entry_date DATE NOT NULL CHECK (entry_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision BETWEEN 1 AND 9007199254740991),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_journals_owned_project FOREIGN KEY (workspace_id,project_id)
        REFERENCES projects(workspace_id,id) ON DELETE RESTRICT
);
-- SQL guards basic integrity; Java enforces full whitespace and UTF-16 semantics.
CREATE INDEX ix_journals_workspace_date ON journals(workspace_id,entry_date DESC,created_at DESC,id DESC);
CREATE INDEX ix_journals_workspace_project_date ON journals(workspace_id,project_id,entry_date DESC,created_at DESC,id DESC);
