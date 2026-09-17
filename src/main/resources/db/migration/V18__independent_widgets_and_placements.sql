-- Final-only: V17 and writer drain are prerequisites. Keep legacy JSONB and mappings.
LOCK TABLE workspaces, dashboards, projects, project_categories IN ACCESS EXCLUSIVE MODE;
DO $precondition$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
        AND table_name IN ('projects','links') AND column_name='scope')
        OR NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='17' AND success) THEN
        RAISE EXCEPTION 'WIDGET_V17_REQUIRED';
    END IF;
END $precondition$;

CREATE TABLE widgets (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    type TEXT NOT NULL CHECK (type ~ '^[a-z][a-z0-9.-]*$'),
    title TEXT NOT NULL CHECK (length(title)>0),
    config_version INTEGER NOT NULL CHECK(config_version>0),
    config JSONB NOT NULL CHECK(jsonb_typeof(config)='object'),
    revision BIGINT NOT NULL CHECK(revision BETWEEN 1 AND 9007199254740991),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(workspace_id,id)
);
CREATE INDEX ix_widgets_page ON widgets(workspace_id,created_at DESC,id DESC);
ALTER TABLE dashboards ADD COLUMN layout_revision BIGINT,
    ADD COLUMN layout_updated_at TIMESTAMPTZ;
UPDATE dashboards SET layout_revision=revision,layout_updated_at=updated_at;
ALTER TABLE dashboards ALTER COLUMN layout_revision SET NOT NULL,
    ALTER COLUMN layout_revision SET DEFAULT 1,
    ALTER COLUMN layout_updated_at SET NOT NULL,
    ALTER COLUMN layout_updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN schema_version SET DEFAULT 2,
    ALTER COLUMN revision SET DEFAULT 1,
    ALTER COLUMN widgets SET DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_dashboard_layout_revision CHECK(layout_revision BETWEEN 1 AND 9007199254740991);

CREATE TABLE dashboard_widget_placements (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    dashboard_key TEXT NOT NULL CHECK(dashboard_key='home'),
    widget_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK(position>=0),
    size TEXT NOT NULL CHECK(size IN ('small','medium','wide')),
    FOREIGN KEY(workspace_id,dashboard_key) REFERENCES dashboards(workspace_id,dashboard_key) ON DELETE RESTRICT,
    FOREIGN KEY(workspace_id,widget_id) REFERENCES widgets(workspace_id,id) ON DELETE RESTRICT,
    UNIQUE(workspace_id,widget_id),
    UNIQUE(workspace_id,dashboard_key,position)
);
CREATE TABLE legacy_widget_mapping (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    dashboard_key TEXT NOT NULL,
    legacy_widget_id TEXT NOT NULL,
    widget_id UUID NOT NULL UNIQUE,
    placement_id UUID NOT NULL UNIQUE,
    PRIMARY KEY(workspace_id,dashboard_key,legacy_widget_id)
);
CREATE TABLE widget_operation_replays (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    method TEXT NOT NULL CHECK(method='POST'),
    path TEXT NOT NULL CHECK(path IN ('/api/v1/widgets','/api/v3/dashboards/home/initializations')),
    key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL CHECK(response_status=201),
    response_body TEXT NOT NULL,
    location TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at>created_at),
    PRIMARY KEY(workspace_id,method,path,key)
);

-- Temporary migration helper, no extension and no permanent runtime function.
CREATE FUNCTION widget_backfill_uuid(kind TEXT,w UUID,d TEXT,legacy TEXT) RETURNS UUID
LANGUAGE plpgsql IMMUTABLE STRICT AS $fn$
DECLARE bytes BYTEA;
BEGIN
    bytes:=decode(md5('devspace-widget-backfill-v1|'||kind||'|'||w::text||'|'||octet_length(convert_to(d,'UTF8'))||':'||d||'|'||octet_length(convert_to(legacy,'UTF8'))||':'||legacy),'hex');
    bytes:=set_byte(bytes,6,(get_byte(bytes,6)&15)|48);
    bytes:=set_byte(bytes,8,(get_byte(bytes,8)&63)|128);
    RETURN encode(bytes,'hex')::uuid;
END $fn$;

DO $backfill$
DECLARE d RECORD; item RECORD; j JSONB; s JSONB; wid UUID; pid UUID; k TEXT; config_value JSONB;
BEGIN
    FOR d IN SELECT * FROM dashboards ORDER BY workspace_id,dashboard_key LOOP
        IF d.schema_version<>2 OR d.revision NOT BETWEEN 1 AND 9007199254740991
            OR jsonb_typeof(d.widgets)<>'array' THEN RAISE EXCEPTION 'INVALID_LEGACY_DASHBOARD'; END IF;
        IF (SELECT data_revision FROM workspaces WHERE id=d.workspace_id)=9223372036854775807 THEN RAISE EXCEPTION 'WORKSPACE_REVISION_OVERFLOW'; END IF;
        FOR item IN SELECT value,ordinality FROM jsonb_array_elements(d.widgets) WITH ORDINALITY LOOP
            j:=item.value; s:=j->'selection'; k:=s->>'kind';
            IF jsonb_typeof(j)<>'object' OR NOT(j ?& ARRAY['id','type','title','size','selection'])
                OR EXISTS(SELECT 1 FROM jsonb_object_keys(j) a WHERE a NOT IN ('id','type','title','size','selection','limit'))
                OR EXISTS(SELECT 1 FROM jsonb_each(j) a WHERE a.value='null'::jsonb)
                OR jsonb_typeof(j->'id')<>'string' OR btrim(j->>'id')='' OR btrim(j->>'id')<>j->>'id'
                OR jsonb_typeof(j->'title')<>'string' OR btrim(j->>'title')='' OR btrim(j->>'title')<>j->>'title'
                OR (SELECT sum(CASE WHEN ascii(c)>65535 THEN 2 ELSE 1 END) FROM regexp_split_to_table(j->>'title','') c)>48
                OR j->>'type' NOT IN ('overview','board','deploy','links','journal','milestone')
                OR j->>'size' NOT IN ('small','medium','wide')
                OR jsonb_typeof(s)<>'object' OR k IS NULL OR k NOT IN ('all','uncategorized','project','category') THEN
                RAISE EXCEPTION 'INVALID_LEGACY_WIDGET';
            END IF;
            IF k IN ('all','uncategorized') AND s<>jsonb_build_object('kind',k) THEN
                RAISE EXCEPTION 'INVALID_LEGACY_SELECTION';
            END IF;
            IF k IN ('project','category') THEN
                IF (SELECT count(*) FROM jsonb_object_keys(s))<>2 OR NOT(s ? (k||'Id')) OR jsonb_typeof(s->(k||'Id'))<>'string'
                    OR (s->>(k||'Id')) !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN RAISE EXCEPTION 'INVALID_LEGACY_SELECTION'; END IF;
                IF k='project' AND NOT EXISTS(SELECT 1 FROM projects WHERE workspace_id=d.workspace_id AND id=(s->>'projectId')::uuid) THEN RAISE EXCEPTION 'BROKEN_LEGACY_PROJECT'; END IF;
            END IF;
            IF j ? 'limit' AND (jsonb_typeof(j->'limit')<>'number' OR (j->>'limit') !~ '^[0-9]+$' OR (j->>'limit')::numeric NOT BETWEEN 1 AND 20) THEN RAISE EXCEPTION 'INVALID_LEGACY_LIMIT'; END IF;
            IF (j->>'type' IN ('deploy','links') AND j ? 'limit') OR (j->>'type'='deploy' AND k<>'all') THEN RAISE EXCEPTION 'INVALID_LEGACY_TYPE_CONFIG'; END IF;
            wid:=widget_backfill_uuid('widget',d.workspace_id,d.dashboard_key,j->>'id');
            pid:=widget_backfill_uuid('placement',d.workspace_id,d.dashboard_key,j->>'id');
            config_value:=jsonb_build_object('selection',s);
            IF j ? 'limit' THEN config_value:=config_value||jsonb_build_object('limit',j->'limit'); END IF;
            INSERT INTO legacy_widget_mapping VALUES(d.workspace_id,d.dashboard_key,j->>'id',wid,pid);
            INSERT INTO widgets VALUES(wid,d.workspace_id,j->>'type',j->>'title',1,config_value,1,d.created_at,d.updated_at);
            INSERT INTO dashboard_widget_placements VALUES(pid,d.workspace_id,d.dashboard_key,wid,item.ordinality-1,j->>'size');
        END LOOP;
        IF (SELECT count(*) FROM legacy_widget_mapping WHERE workspace_id=d.workspace_id AND dashboard_key=d.dashboard_key)<>jsonb_array_length(d.widgets) THEN RAISE EXCEPTION 'WIDGET_RECONCILIATION_FAILED'; END IF;
        UPDATE workspaces SET data_revision=data_revision+1 WHERE id=d.workspace_id;
    END LOOP;
END $backfill$;
DROP FUNCTION widget_backfill_uuid(TEXT,UUID,TEXT,TEXT);
