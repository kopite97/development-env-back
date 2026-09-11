# Application

The application layer coordinates use cases.
Keep orchestration here and business rules in the domain.

## Use Cases

- Model application behavior around explicit use cases.
- Prefer small services with clear responsibilities.
- Do not create large services that mix unrelated operations.
- Application services may coordinate domain objects, repositories, and external ports.
- Do not place HTTP, persistence, or external SDK details in application logic.

Examples:

```text
CreateProject
UpdateProject
CompleteProject
MoveKanbanCard
GetProject
SearchProjects
```

## Command / Query

Separate state-changing operations from read operations.

```text
Command
→ changes application state

Query
→ reads state without changing it
```

Prefer separate services when a feature contains both responsibilities:

```text
ProjectCommandService
ProjectQueryService
```

Do not introduce full CQRS infrastructure unless explicitly required.

## Transactions

The application layer owns transaction boundaries.

- Use `@Transactional` for commands that modify persistent state.
- Use `@Transactional(readOnly = true)` for queries when appropriate.
- Do not place transaction boundaries in controllers or domain entities.
- Keep transactions as small as reasonably possible.
- Do not change transaction propagation without a concrete requirement.
- Avoid slow external network calls inside database transactions.

## Orchestration

Application services may:

- load aggregates through repositories,
- invoke domain behavior,
- persist state,
- coordinate external ports,
- return use-case results.

Application services must not:

- implement domain invariants that belong to domain objects,
- expose JPA-specific behavior,
- depend directly on infrastructure implementations,
- perform presentation-layer response handling.

## Dependencies

Application code may depend on:

```text
Domain
Application-defined Ports
```

Infrastructure must depend on application/domain abstractions, not the reverse.

For domain behavior, follow `DOMAIN.md`.
For external integrations, follow `PORTS_ADAPTERS.md`.
For abstraction rules, follow `DESIGN_PRINCIPLES.md`.