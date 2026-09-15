ALTER TABLE links ADD COLUMN project_id UUID;
ALTER TABLE links ADD CONSTRAINT fk_links_owned_project
    FOREIGN KEY (workspace_id,project_id) REFERENCES projects(workspace_id,id) ON DELETE RESTRICT;
CREATE INDEX ix_links_workspace_project ON links(workspace_id,project_id);
ALTER TABLE dashboards DROP CONSTRAINT dashboards_schema_version_check;
ALTER TABLE dashboards ADD CONSTRAINT dashboards_schema_version_check CHECK (schema_version IN (1,2));
