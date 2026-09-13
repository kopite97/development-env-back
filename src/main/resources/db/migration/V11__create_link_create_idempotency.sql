CREATE TABLE link_create_idempotency (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    method TEXT NOT NULL CHECK (method = 'POST'),
    path TEXT NOT NULL CHECK (path = '/api/v1/links'),
    key TEXT NOT NULL CHECK (length(key) BETWEEN 1 AND 128),
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL CHECK (response_status = 201),
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (workspace_id,method,path,key),
    CHECK (expires_at > created_at)
);
CREATE INDEX ix_link_create_idempotency_expiry ON link_create_idempotency(expires_at);
