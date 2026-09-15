-- Old writers must be drained. Supply the reviewed JSON through Flyway initSql:
-- SELECT set_config('devspace.category_cutover_manifest', '<manifest>', false).
-- No setting is needed for an empty database. All changes roll back on any mismatch.
LOCK TABLE workspaces, projects, project_categories, links, link_collections, dashboards
    IN ACCESS EXCLUSIVE MODE;
DO $cutover$
DECLARE
    manifest JSONB;
    w RECORD;
    m JSONB;
    d RECORD;
    widget JSONB;
    selections JSONB;
    chosen JSONB;
    natural_selection JSONB;
    converted JSONB;
    link_input JSONB;
    existing_link RECORD;
    target UUID;
    changed_links BOOLEAN;
BEGIN
    manifest := NULLIF(current_setting('devspace.category_cutover_manifest',true),'')::jsonb;
    IF manifest IS NULL THEN
        IF EXISTS(SELECT 1 FROM workspaces) THEN RAISE EXCEPTION 'CUTOVER_MANIFEST_REQUIRED'; END IF;
        manifest := '{"workspaces":[]}'::jsonb;
    END IF;
    IF jsonb_typeof(manifest) IS DISTINCT FROM 'object'
       OR jsonb_typeof(manifest->'workspaces') IS DISTINCT FROM 'array'
       OR EXISTS(SELECT 1 FROM jsonb_object_keys(manifest) k WHERE k<>'workspaces') THEN
        RAISE EXCEPTION 'INVALID_CUTOVER_MANIFEST';
    END IF;
    IF (SELECT count(*) FROM workspaces) <> jsonb_array_length(manifest->'workspaces')
       OR (SELECT count(DISTINCT (x->>'workspaceId')::uuid) FROM jsonb_array_elements(manifest->'workspaces') x)
          <> jsonb_array_length(manifest->'workspaces')
       OR EXISTS(SELECT 1 FROM jsonb_array_elements(manifest->'workspaces') x
                 WHERE NOT EXISTS(SELECT 1 FROM workspaces WHERE id=(x->>'workspaceId')::uuid)) THEN
        RAISE EXCEPTION 'WORKSPACE_MANIFEST_MISMATCH';
    END IF;
    FOR w IN SELECT * FROM workspaces ORDER BY id LOOP
        SELECT x INTO m FROM jsonb_array_elements(manifest->'workspaces') x WHERE (x->>'workspaceId')::uuid=w.id;
        IF (m->>'expectedDataRevision')::bigint IS DISTINCT FROM w.data_revision
           OR w.data_revision=9223372036854775807
           OR jsonb_typeof(m->'links') IS DISTINCT FROM 'array'
           OR EXISTS(SELECT 1 FROM jsonb_object_keys(m) k WHERE k NOT IN ('workspaceId','expectedDataRevision','dashboard','links')) THEN
            RAISE EXCEPTION 'WORKSPACE_PRECONDITION_FAILED';
        END IF;
        IF jsonb_array_length(m->'links') <> (SELECT count(*) FROM links WHERE workspace_id=w.id)
           OR (SELECT count(DISTINCT (x->>'id')::uuid) FROM jsonb_array_elements(m->'links') x)
              <> jsonb_array_length(m->'links') THEN RAISE EXCEPTION 'LINK_MANIFEST_MISMATCH'; END IF;
        changed_links := false;
        FOR link_input IN SELECT * FROM jsonb_array_elements(m->'links') LOOP
            SELECT * INTO existing_link FROM links WHERE workspace_id=w.id AND id=(link_input->>'id')::uuid;
            IF NOT FOUND OR (link_input->>'expectedRevision')::bigint IS DISTINCT FROM existing_link.revision
               OR NOT(link_input ? 'projectId')
               OR EXISTS(SELECT 1 FROM jsonb_object_keys(link_input) k WHERE k NOT IN ('id','expectedRevision','projectId')) THEN
                RAISE EXCEPTION 'LINK_PRECONDITION_FAILED';
            END IF;
            target := (link_input->>'projectId')::uuid;
            IF target IS NOT NULL AND NOT EXISTS(SELECT 1 FROM projects WHERE workspace_id=w.id AND id=target) THEN
                RAISE EXCEPTION 'LINK_PROJECT_NOT_OWNED';
            END IF;
            IF existing_link.project_id IS DISTINCT FROM target THEN
                IF existing_link.revision=9007199254740991 THEN RAISE EXCEPTION 'LINK_REVISION_OVERFLOW'; END IF;
                UPDATE links SET project_id=target,revision=revision+1,updated_at=transaction_timestamp() WHERE id=existing_link.id;
                changed_links := true;
            END IF;
        END LOOP;
        IF changed_links THEN
            IF EXISTS(SELECT 1 FROM link_collections WHERE workspace_id=w.id AND revision=9007199254740991) THEN
                RAISE EXCEPTION 'LINK_COLLECTION_OVERFLOW';
            END IF;
            UPDATE link_collections SET revision=revision+1 WHERE workspace_id=w.id;
        END IF;
        SELECT * INTO d FROM dashboards WHERE workspace_id=w.id AND dashboard_key='home';
        IF FOUND THEN
            IF jsonb_typeof(m->'dashboard') IS DISTINCT FROM 'object'
               OR (m->'dashboard'->>'expectedRevision')::bigint IS DISTINCT FROM d.revision
               OR m->'dashboard'->>'expectedWidgetsMd5' IS DISTINCT FROM md5(d.widgets::text)
               OR d.schema_version<>1 OR d.revision=9007199254740991
               OR jsonb_typeof(m->'dashboard'->'selections') IS DISTINCT FROM 'object'
               OR EXISTS(SELECT 1 FROM jsonb_object_keys(m->'dashboard') k
                         WHERE k NOT IN ('expectedRevision','expectedWidgetsMd5','selections')) THEN
                RAISE EXCEPTION 'DASHBOARD_PRECONDITION_FAILED';
            END IF;
            selections := m->'dashboard'->'selections';
            IF EXISTS(SELECT 1 FROM jsonb_object_keys(selections) k
                      WHERE NOT EXISTS(SELECT 1 FROM jsonb_array_elements(d.widgets) v WHERE v->>'id'=k)) THEN
                RAISE EXCEPTION 'UNKNOWN_WIDGET_MAPPING';
            END IF;
            converted := '[]'::jsonb;
            FOR widget IN SELECT * FROM jsonb_array_elements(d.widgets) LOOP
                natural_selection := NULL;
                IF widget->>'projectId' IS NOT NULL THEN
                    natural_selection := jsonb_build_object('kind','project','projectId',(widget->>'projectId')::uuid);
                ELSIF widget->>'scope'='all' THEN natural_selection := '{"kind":"all"}'::jsonb;
                ELSIF widget->>'scope' NOT IN ('unity','server') OR widget->>'scope' IS NULL THEN
                    RAISE EXCEPTION 'INVALID_STORED_SCOPE';
                END IF;
                chosen := selections->(widget->>'id');
                IF natural_selection IS NOT NULL THEN
                    IF chosen IS NOT NULL AND chosen<>natural_selection THEN RAISE EXCEPTION 'IDENTITY_MAPPING_CONFLICT'; END IF;
                    chosen := natural_selection;
                END IF;
                IF chosen IS NULL THEN RAISE EXCEPTION 'AMBIGUOUS_WIDGET_MAPPING_REQUIRED'; END IF;
                IF jsonb_typeof(chosen) IS DISTINCT FROM 'object' THEN RAISE EXCEPTION 'INVALID_WIDGET_SELECTION'; END IF;
                CASE chosen->>'kind'
                    WHEN 'all','uncategorized' THEN
                        IF (SELECT count(*) FROM jsonb_object_keys(chosen))<>1 THEN RAISE EXCEPTION 'INVALID_WIDGET_SELECTION'; END IF;
                    WHEN 'project' THEN
                        IF (SELECT count(*) FROM jsonb_object_keys(chosen))<>2
                           OR NOT EXISTS(SELECT 1 FROM projects WHERE workspace_id=w.id AND id=(chosen->>'projectId')::uuid) THEN
                            RAISE EXCEPTION 'WIDGET_PROJECT_NOT_OWNED';
                        END IF;
                    WHEN 'category' THEN
                        IF (SELECT count(*) FROM jsonb_object_keys(chosen))<>2
                           OR NOT EXISTS(SELECT 1 FROM project_categories WHERE workspace_id=w.id AND id=(chosen->>'categoryId')::uuid) THEN
                            RAISE EXCEPTION 'WIDGET_CATEGORY_NOT_OWNED';
                        END IF;
                    ELSE RAISE EXCEPTION 'INVALID_WIDGET_SELECTION';
                END CASE;
                IF widget->>'type'='deploy' AND chosen->>'kind'<>'all' THEN RAISE EXCEPTION 'DEPLOY_SELECTION_UNSUPPORTED'; END IF;
                converted := converted || jsonb_build_array((widget-'scope'-'projectId') || jsonb_build_object('selection',chosen));
            END LOOP;
            UPDATE dashboards SET widgets=converted,schema_version=2,revision=revision+1,updated_at=transaction_timestamp()
                WHERE workspace_id=w.id AND dashboard_key='home';
        ELSIF m ? 'dashboard' THEN RAISE EXCEPTION 'UNEXPECTED_DASHBOARD_MAPPING';
        END IF;
        UPDATE workspaces SET data_revision=data_revision+1 WHERE id=w.id;
    END LOOP;
END
$cutover$;
ALTER TABLE projects ALTER COLUMN scope DROP NOT NULL;
ALTER TABLE links ALTER COLUMN scope DROP NOT NULL;
ALTER TABLE links ALTER COLUMN scope DROP DEFAULT;

-- Durable deployment metadata: expiry cleanup must not erase the minimum drain window.
CREATE TABLE category_cutover_state (
    transition_key TEXT PRIMARY KEY CHECK (transition_key='project-category-only'),
    cutover_at TIMESTAMPTZ NOT NULL,
    retire_after TIMESTAMPTZ NOT NULL,
    clock_margin_seconds INTEGER NOT NULL CHECK (clock_margin_seconds>=0),
    CHECK (retire_after>=cutover_at)
);
DO $drain$
DECLARE
    cutover_time TIMESTAMPTZ := clock_timestamp();
    latest_expiry TIMESTAMPTZ;
    margin_seconds INTEGER := coalesce(nullif(current_setting('devspace.category_cutover_clock_margin_seconds',true),''),'300')::INTEGER;
BEGIN
    IF margin_seconds<0 THEN RAISE EXCEPTION 'INVALID_CLOCK_MARGIN'; END IF;
    SELECT max(expires_at) INTO latest_expiry FROM (
        SELECT expires_at FROM project_create_idempotency UNION ALL
        SELECT expires_at FROM task_create_idempotency UNION ALL
        SELECT expires_at FROM journal_create_idempotency UNION ALL
        SELECT expires_at FROM milestone_create_idempotency UNION ALL
        SELECT expires_at FROM link_create_idempotency
    ) replay;
    INSERT INTO category_cutover_state VALUES ('project-category-only',cutover_time,
        CASE WHEN EXISTS(SELECT 1 FROM workspaces) OR latest_expiry IS NOT NULL
            THEN greatest(cutover_time+interval '24 hours',coalesce(latest_expiry,cutover_time))+make_interval(secs=>margin_seconds)
            ELSE cutover_time END,margin_seconds);
END
$drain$;
