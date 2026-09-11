# Design Principles

Apply SOLID principles pragmatically.
Prefer clear, maintainable code over unnecessary abstraction.

## Single Responsibility

- Give each class one clear responsibility and reason to change.
- Split classes when unrelated responsibilities begin to accumulate.
- Do not split cohesive behavior merely to reduce class size.

## Open / Closed

- Introduce extension points only when variation is real or reasonably expected.
- Prefer modifying simple code over creating speculative abstractions.
- Do not introduce strategies, factories, or plugin structures without a concrete need.

## Liskov Substitution

- Implementations must preserve the contract of their abstractions.
- Do not create implementations that require callers to know implementation-specific exceptions or behavior.

## Interface Segregation

- Keep interfaces focused on a specific capability or boundary.
- Avoid large interfaces containing unrelated operations.
- Do not create an interface only because a concrete class exists.

## Dependency Inversion

Use abstractions at meaningful architectural boundaries.

Prefer abstractions for:

```text
External APIs
Object Storage
Messaging
Mail
Third-party Services
Replaceable Infrastructure
```

Do not create unnecessary pairs such as:

```text
ProjectService
ProjectServiceImpl
```

when only one implementation exists and no boundary requires abstraction.

## Simplicity

Follow KISS and YAGNI.

- Implement the simplest design that satisfies the current requirement.
- Do not design for hypothetical future requirements.
- Prefer explicit code over hidden or overly generic behavior.
- Reuse code when the shared behavior is genuinely the same.
- Do not create abstractions solely to eliminate small amounts of duplication.

## Changes

Before introducing a new abstraction or pattern:

1. Identify the concrete problem it solves.
2. Check whether an existing pattern already solves it.
3. Prefer the smallest change that preserves architectural boundaries.