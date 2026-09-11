CREATE TABLE users (
    id UUID NOT NULL,
    display_name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    disabled_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE TABLE auth_identities (
    issuer TEXT NOT NULL,
    subject TEXT NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT pk_auth_identities PRIMARY KEY (issuer, subject),
    CONSTRAINT fk_auth_identities_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_auth_identities_issuer_non_empty CHECK (char_length(issuer) > 0),
    CONSTRAINT ck_auth_identities_subject_non_empty CHECK (char_length(subject) > 0)
);

CREATE TABLE workspaces (
    id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    name TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    data_revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workspaces PRIMARY KEY (id),
    CONSTRAINT uq_workspaces_owner UNIQUE (owner_user_id),
    CONSTRAINT fk_workspaces_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_workspaces_revision_positive CHECK (revision >= 1),
    CONSTRAINT ck_workspaces_data_revision_non_negative CHECK (data_revision >= 0)
);
