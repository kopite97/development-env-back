# AGENTS.md

This repository contains the integrated development management web application.

Use this file as a guide to locate task-specific instructions.
Read only the guides relevant to the current task.

## Backend Guides

Backend implementation guides are located under `docs/agents/`.

- Architecture and package structure → `ARCHITECTURE.md`
- Spring Boot conventions → `SPRING_BOOT.md`
- REST API design → `API.md`
- JPA and PostgreSQL → `DATABASE.md`
- Database migrations → `FLYWAY.md`
- Authentication, authorization, and external credentials → `SECURITY.md`
- Implementation design → `IMPLEMENTATION/AGENTS.md`

## Plans and Decisions

For multi-step or non-trivial implementation work, read `docs/plans/AGENTS.md` before creating or updating a plan.

Consult `docs/decisions/AGENTS.md` only when the task requires a decision with long-term architectural impact or a change that may affect future agent guidance.

Do not create decision records for routine implementation details.

When a decision introduces a recurring implementation rule, do not update `docs/agents/` automatically.
First propose the rule to the user and obtain explicit approval.
After approval, update the relevant guide under `docs/agents/` in the same change as the accepted decision.

## Working Rules

- Inspect the existing implementation before making changes.
- Follow existing architecture and patterns unless a change is explicitly required.
- Make the smallest change necessary to satisfy the task.
- Do not perform unrelated refactoring.
- Do not introduce new dependencies, frameworks, infrastructure, or architectural patterns without explicit approval.
- Follow task-specific guides before implementing or modifying related code.
- Validate affected code after implementation using the appropriate build and test commands.
- Fix validation failures caused by the change before completing the task.

## Guide Maintenance

When a recurring implementation rule or architectural guideline is discovered, propose the guide change to the user and obtain explicit approval before updating `docs/agents/`.

Keep guides focused and concise.
Prefer guides under 50 lines and never exceed 100 lines.