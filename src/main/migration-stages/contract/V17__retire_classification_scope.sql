-- Ship only in the final artifact, after the bridge replay window and approved clock margin.
LOCK TABLE workspaces,projects,links,dashboards IN ACCESS EXCLUSIVE MODE;
DO $retire$
DECLARE
    deadline TIMESTAMPTZ;
    margin_seconds INTEGER;
    latest_expiry TIMESTAMPTZ;
BEGIN
    SELECT retire_after,clock_margin_seconds INTO deadline,margin_seconds
        FROM category_cutover_state WHERE transition_key='project-category-only';
    IF deadline IS NULL OR clock_timestamp()<deadline THEN RAISE EXCEPTION 'LEGACY_REPLAY_DRAIN_REQUIRED'; END IF;
    -- v2 records share the canonical tables but must not extend the legacy drain window.
    SELECT max(expires_at) INTO latest_expiry FROM (
        SELECT expires_at FROM project_create_idempotency WHERE response_body::jsonb ? 'scope' UNION ALL
        SELECT expires_at FROM task_create_idempotency WHERE response_body::jsonb ? 'scope' UNION ALL
        SELECT expires_at FROM journal_create_idempotency WHERE response_body::jsonb ? 'scope' UNION ALL
        SELECT expires_at FROM milestone_create_idempotency WHERE response_body::jsonb ? 'scope' UNION ALL
        SELECT expires_at FROM link_create_idempotency WHERE response_body::jsonb->'item' ? 'scope'
    ) legacy;
    IF latest_expiry IS NOT NULL AND clock_timestamp()<latest_expiry+make_interval(secs=>margin_seconds)
        THEN RAISE EXCEPTION 'LEGACY_REPLAY_DRAIN_REQUIRED'; END IF;
END
$retire$;
ALTER TABLE projects DROP CONSTRAINT ck_projects_scope, DROP COLUMN scope;
ALTER TABLE links DROP CONSTRAINT links_scope_check, DROP COLUMN scope;
ALTER TABLE dashboards DROP CONSTRAINT dashboards_schema_version_check;
ALTER TABLE dashboards ADD CONSTRAINT dashboards_schema_version_check CHECK (schema_version=2);
