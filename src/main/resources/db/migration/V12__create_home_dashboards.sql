CREATE TABLE dashboards (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    dashboard_key TEXT NOT NULL CHECK (dashboard_key = 'home'),
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    revision BIGINT NOT NULL CHECK (revision BETWEEN 1 AND 9007199254740991),
    widgets JSONB NOT NULL CHECK (jsonb_typeof(widgets) = 'array'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (workspace_id, dashboard_key)
);
