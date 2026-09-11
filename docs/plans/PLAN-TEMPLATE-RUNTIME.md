# PLAN-XXXX: <Title>

Status: `proposed`

## Goal

<Describe the intended outcome of this plan.>

## Scope

- <What is included>
- <Important constraints>
- <What is explicitly excluded>

## Execution Sessions

Execute sessions in order.

Do not start the next session until the current session's implementation and validation are complete.

Check an item only after the corresponding work has actually been completed.
Update this Plan as execution progresses.

### Session 1: <Name>

Objective:

<Describe the result this session should produce.>

Implementation:

- [ ] <Implementation task>
- [ ] <Implementation task>

Validation:

- [ ] <Validation required for this session>

### Session 2: <Name>

Objective:

<Describe the result this session should produce.>

Implementation:

- [ ] <Implementation task>
- [ ] <Implementation task>

Validation:

- [ ] <Validation required for this session>

## Final Validation

- [ ] <End-to-end or final build/test validation>
- [ ] No failures caused by this Plan remain.
- [ ] All Execution Sessions are complete.

### API Validation

Complete these checks only when this Plan adds or changes HTTP API endpoints.

- [ ] Implemented endpoints are present in `/v3/api-docs`.
- [ ] Request and response schemas match the approved backend contract.
- [ ] Bean Validation constraints are reflected where applicable.
- [ ] HTTP methods and response status codes match the contract.
- [ ] Swagger UI renders the implemented endpoints correctly.

## Completion

The Plan may be changed to `completed` only when:

- every required session item is checked,
- every applicable validation item is checked,
- and no unresolved blocker prevents the Goal from being satisfied.

Validation items that do not apply to the Plan must not be treated as required.

If execution cannot continue, leave incomplete items unchecked and record the blocker before stopping.