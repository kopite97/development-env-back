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

## Package Organization

- Keep the four layers inside each feature; organize by role only when it improves navigation.
- In large application packages, group commands/services in `command`, queries/filters in `query`, application interfaces in `port`, shared results/cursors in `model`, and application exceptions in `exception`.
- Group presentation DTOs, their parsers and validation annotations in `dto`. Use `controller` or `openapi` only when multiple related classes justify them; leave singleton roles at the layer root.
- Group persistence repositories/adapters in `infrastructure.persistence`; use `configuration` only for a meaningful group. Security/OIDC adapters may use their own role groups.
- Do not mechanically create every role folder. Keep small cohesive packages flat, avoid one-file subpackages and excessive nesting, and keep domain packages simple.
- Keep package-private collaborators together where possible; package moves must preserve behavior, contracts, transaction boundaries and dependency direction.
- Keep feature-specific code in its feature. Use `global` only for genuinely shared concerns, not merely because several classes use a type.

## Architectural Changes

Follow the existing architecture before introducing new layers or patterns.

Do not introduce CQRS infrastructure, event buses, additional persistence models,
or other architectural complexity without a concrete requirement and explicit approval.

For implementation-level rules, follow `IMPLEMENTATION/AGENTS.md`.