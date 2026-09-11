# Domain

Apply pragmatic Domain-Driven Design.
Keep business rules and invariants close to the domain model without unnecessary abstraction.

## Domain and JPA Entities

Domain entities and JPA entities are the same model by default.

- Do not create separate persistence models unless persistence requirements materially conflict with the domain model.
- Persistence annotations must not dictate domain behavior.
- Domain entities must not depend on controllers, HTTP concerns, external SDKs, or infrastructure implementations.

## Aggregates

- Define aggregate boundaries around consistency requirements.
- Access and modify aggregate state through the Aggregate Root.
- Do not expose mutable internal collections directly.
- Keep aggregates reasonably small and avoid unnecessary object graphs.

## Invariants

- Domain objects must protect their own valid state.
- Validate business invariants inside the domain model.
- Prevent invalid state during both creation and modification.
- Prefer meaningful domain behavior over direct field mutation.

Prefer:

```java
project.rename(name);
project.complete();
```

Avoid:

```java
project.setName(name);
project.setStatus(status);
```

## Creation

- Prefer constructors or named factory methods that create valid domain objects.
- Use protected no-argument constructors only when required by JPA.
- Do not allow creation paths that bypass required invariants.

## Lombok

Lombok may be used, but entity mutability must remain explicit.

Recommended:

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
```

- Do not use class-level `@Setter` on entities.
- Do not use `@Data` on entities.
- Do not expose setters merely for mapping or testing convenience.
- Use `@Builder` on entities only when it cannot bypass domain invariants.

## Value Objects

Use immutable Value Objects when a concept has meaningful validation,
behavior, or identity beyond a primitive value.

Do not wrap primitives in Value Objects without clear domain value.

## Domain Services

Create a Domain Service only when a business rule:

- belongs to the domain,
- involves multiple domain objects,
- and does not naturally belong to one Entity or Value Object.

Do not move ordinary entity behavior into services.

## Domain Events

Do not introduce Domain Events by default.
Use them only when decoupled domain behavior provides a concrete benefit.