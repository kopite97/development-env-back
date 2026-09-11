# Implementation Guide

This directory contains implementation-level design rules.

Use this file only as a map.
Read only the guides relevant to the current task.

## Guides

- Domain model, DDD, entities, aggregates, invariants, Lombok, and setter policy
  → `DOMAIN.md`

- Use cases, Command/Query separation, application services, and transactions
  → `APPLICATION.md`

- SOLID, KISS, YAGNI, abstraction, interfaces, and dependency design
  → `DESIGN_PRINCIPLES.md`

- External systems, ports, adapters, and infrastructure boundaries
  → `PORTS_ADAPTERS.md`

## Usage

- Follow the existing implementation before introducing a new pattern.
- Apply only the guides relevant to the current change.
- Do not introduce additional abstractions or architectural patterns without a concrete requirement.
- Keep implementation rules in the most specific guide that owns the responsibility.
- If a recurring implementation concern no longer fits an existing guide, create a focused guide rather than expanding unrelated documents.

Keep each guide focused and concise.
Prefer guides under 50 lines and never exceed 100 lines.