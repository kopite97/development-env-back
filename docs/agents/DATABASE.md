# Database

Use PostgreSQL as the primary relational database.
Use JPA for persistence while keeping database behavior explicit and predictable.

## Schema Management

- Manage schema changes with Flyway.
- Do not use Hibernate to modify production schemas.
- Use:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

- Do not use `ddl-auto=update` outside temporary experiments.

## Entities

- JPA entities may also serve as domain entities by default.
- Do not expose entities directly through APIs.
- Keep persistence annotations from dictating domain behavior.
- Prefer `FetchType.LAZY` for associations unless eager loading is clearly required.
- Avoid unnecessary bidirectional relationships.

## Relationships

- Define aggregate ownership explicitly.
- Do not use `CascadeType.ALL` by default.
- Apply cascade and orphan removal only when lifecycle ownership is clear.
- Avoid direct modification of relationship collections from outside the owning domain model.

## Repositories

- Repositories are responsible for persistence access only.
- Do not place business logic in repositories.
- Do not access repositories directly from controllers.
- Prefer derived queries for simple cases and explicit queries when behavior or performance requires clarity.
- Do not load entire tables and filter results in application memory.

## Query Performance

- Consider N+1 queries when loading associations.
- Use fetch joins, entity graphs, projections, or dedicated queries when appropriate.
- Paginate collections that may grow without a strict bound.
- Add indexes only for demonstrated query or constraint requirements.

## PostgreSQL

- Prefer PostgreSQL-native capabilities when they provide clear value.
- Keep database-specific SQL explicit rather than hiding it behind unnecessary abstraction.
- Use database constraints for integrity that must always hold.
- Keep application validation and database constraints complementary.

## Transactions

Transaction boundaries belong to the application layer.

Do not rely on lazy loading outside a valid transaction boundary.
For detailed transaction rules, follow `IMPLEMENTATION/APPLICATION.md`.

For schema migration rules, follow `FLYWAY.md`.
For entity and aggregate rules, follow `IMPLEMENTATION/DOMAIN.md`.

## JPA Pitfalls

Be alert to common JPA performance and persistence problems.

- Check for N+1 queries whenever associations are accessed in collections or API responses.
- Do not solve N+1 problems by switching associations to `EAGER` by default.
- Prefer fetch joins, entity graphs, projections, or dedicated queries when related data is required.
- Do not access lazy-loaded associations outside a valid persistence context.
- Avoid unnecessary bidirectional relationships and recursive entity serialization.
- Do not load large result sets into memory only to filter or aggregate them in application code.
- Be careful when combining collection fetch joins with pagination.
- Avoid row-by-row updates for large bulk operations when a bulk query is more appropriate.
- Review generated SQL when query behavior or performance is unclear.