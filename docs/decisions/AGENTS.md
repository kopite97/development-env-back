# Decision Guide

Use decision records only for choices with long-term architectural impact or choices that may affect future agent guidance.

Do not create decision records for routine implementation details, local refactoring, or temporary choices.

## Naming

Name new decisions as `DECISION-XXXX-<short-kebab-case-title>.md`.

## Lifecycle

Decision statuses are:

- `proposed` — under consideration
- `accepted` — explicitly approved and adopted
- `superseded` — replaced by a newer decision
- `rejected` — considered but not adopted

Every new decision must start as `proposed`.

A decision may become `accepted` only after explicit user approval.

## Decision Rules

- Keep each decision focused on one significant choice.
- Record the problem, chosen option, and essential reasoning only.
- Do not use decisions to duplicate implementation guides.
- Follow existing accepted decisions unless a new decision explicitly supersedes them.
- If a decision replaces an existing one, mark the previous decision as `superseded`.

## Agent Guidance

When an accepted decision introduces a recurring implementation rule, do not update `docs/agents/` automatically.

Propose the new rule to the user and obtain explicit approval before adding or changing agent guidance.

## Decision Index

`README.md` is the chronological index of accepted decisions.

When a decision becomes `accepted`, append it to `README.md` in decision order.

Keep the index as a simple linked list.
Do not add summaries or implementation details.

## Template

Use `DECISION-TEMPLATE.md` when creating a new decision.