# Architecture

Use package-by-feature as the primary package structure.
This project follows pragmatic Domain-Driven Design (DDD).
Prefer clear domain boundaries and behavior without unnecessary abstraction.

## Layers

Each feature may contain the following layers:

```text
feature/
├─ presentation/
├─ application/
├─ domain/
└─ infrastructure/
```

Responsibilities:

- `presentation` — HTTP controllers and request/response handling
- `application` — use cases and application orchestration
- `domain` — business models, rules, and domain abstractions
- `infrastructure` — persistence and external system implementations

## Dependency Direction

Dependencies must flow inward:

```text
Presentation
     ↓
Application
     ↓
Domain
     ↑
Infrastructure
```

- Domain must not depend on Presentation or Infrastructure.
- Presentation must not access Infrastructure directly.
- Infrastructure may implement abstractions defined by inner layers.
- Do not bypass layers for convenience.

## Package Structure

Prefer:

```text
<base-package>/
├─ global/
│  ├─ config/
│  ├─ exception/
│  └─ response/
├─ project/
├─ devlog/
├─ kanban/
└─ reference/
```

Keep feature-specific code inside its feature package.

Use `global` only for genuinely shared cross-cutting concerns.
Do not move code into `global` merely because multiple classes use it.

## Architectural Changes

Follow the existing architecture before introducing new layers or patterns.

Do not introduce CQRS infrastructure, event buses, additional persistence models,
or other architectural complexity without a concrete requirement and explicit approval.

For implementation-level rules, follow `IMPLEMENTATION/AGENTS.md`.