# Plan Guide

Use plans for multi-step or non-trivial work that benefits from an explicit execution sequence.

Do not create plans for small, isolated, or routine changes.

## Naming

Name new plans as `PLAN-XXXX-<short-kebab-case-title>.md`.

## Templates

Use the appropriate template:

- Runtime behavior changes → `PLAN-TEMPLATE-RUNTIME.md`
- Non-runtime changes → `PLAN-TEMPLATE-NON_RUNTIME.md`

Runtime changes include application behavior, APIs, persistence, integrations, and other changes that can be executed or observed at runtime.

Non-runtime changes include documentation, agent guides, repository metadata, and similar changes with no runtime behavior.

## Plan Lifecycle

Plan statuses are:

- `proposed` — created but not yet approved
- `active` — explicitly approved and being executed
- `completed` — implemented and successfully validated
- `rejected` — cancelled, superseded, or intentionally abandoned

Every new plan must start as `proposed`.

A plan may transition from `proposed` to `active` only after explicit user approval.

Do not begin plan execution before approval.

Update the plan status when its lifecycle changes.

## Plan Rules

- Keep plans focused on one coherent goal.
- Describe what will change, not low-level implementation details that belong in code.
- Follow existing architecture and agent guides when planning implementation.
- Do not introduce unapproved architectural or recurring implementation rules through a plan.
- If planning reveals a long-term architectural decision, follow `docs/decisions/AGENTS.md`.
- Keep execution steps ordered and independently understandable.
- Define validation before implementation begins.

## Completion

Mark a plan `completed` only after its required validation succeeds.

If validation cannot reasonably be performed, explain why and record the strongest available alternative validation.

## Plan Index

`README.md` is the chronological index of completed plans.

When a plan becomes `completed`, append it to `README.md` in plan order.

Keep the index as a simple linked list.
Do not add summaries or implementation details.