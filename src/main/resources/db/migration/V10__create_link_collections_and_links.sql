CREATE TABLE link_collections (
    workspace_id UUID PRIMARY KEY REFERENCES workspaces(id) ON DELETE RESTRICT,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision BETWEEN 0 AND 9007199254740991)
);
CREATE TABLE links (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES link_collections(workspace_id) ON DELETE RESTRICT,
    label TEXT NOT NULL CHECK (length(btrim(label)) > 0 AND length(label) <= 100),
    description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 300),
    url TEXT NOT NULL CHECK (length(btrim(url)) > 0 AND length(url) <= 2000),
    scope TEXT NOT NULL DEFAULT 'all' CHECK (scope IN ('all','unity','server')),
    position BIGINT NOT NULL CHECK (position BETWEEN 0 AND 9007199254740991),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision BETWEEN 1 AND 9007199254740991),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_links_workspace_position UNIQUE (workspace_id,position) DEFERRABLE INITIALLY IMMEDIATE
);
