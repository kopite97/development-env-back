# Flyway

Use Flyway as the source of truth for database schema changes.

## Migration Location

Store migrations in:

```text
src/main/resources/db/migration
```

## Naming

Use versioned migrations:

```text
V{version}__{description}.sql
```

Examples:

```text
V1__init.sql
V2__create_projects.sql
V3__add_project_status.sql
```

Use clear, descriptive names.
Keep migration versions unique and ordered.

## Migration Rules

- Every schema change must be implemented through a new migration.
- Never modify, rename, delete, or reorder a migration that has already been applied.
- Never reuse an existing migration version.
- Do not manually modify production schemas outside Flyway.
- Do not use Hibernate `ddl-auto=update` as a replacement for migrations.
- Keep each migration focused on one logical database change.
- Include required constraints, indexes, and foreign keys in migrations.

## Data Changes

- Use migrations for required data transformations that must accompany schema changes.
- Avoid large or long-running data migrations without considering deployment impact.
- Never assume existing production data is empty.
- Preserve existing data unless deletion is explicitly required.

## Validation

Flyway checksum validation must remain enabled.

If an applied migration no longer matches its recorded checksum,
do not rewrite its history to silence the error.
Investigate the cause and create a corrective migration when necessary.

## Repeatable Migrations

Use `R__*.sql` only for database objects that are intentionally recreated,
such as views or database functions.

Do not use repeatable migrations for normal schema evolution.

## Existing Databases

Do not enable baseline or repair operations automatically.

Use Flyway baseline or repair only when integrating an existing schema
or correcting migration metadata, and only after explicit review.

For general database rules, follow `DATABASE.md`.