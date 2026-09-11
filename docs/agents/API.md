# API

Design APIs as stable external contracts.
Do not expose persistence or domain implementation details.

## Endpoints

- Use resource-oriented REST endpoints and nouns for paths.
- Use HTTP methods according to their semantics.
- Keep endpoint and parameter naming consistent.
- Avoid action-style URLs unless the operation cannot be expressed naturally as a resource change.

## DTOs

- Never expose JPA entities directly.
- Use separate request and response DTOs.
- Prefer purpose-specific DTOs such as `CreateProjectRequest` and `ProjectResponse`.
- Keep business logic out of DTOs.

## Responses

Use the project's common response and error formats consistently.

- Return appropriate HTTP status codes such as `200`, `201`, `204`, `400`, `401`, `403`, `404`, and `409`.
- Do not build error responses individually in controllers.
- Handle application and domain exceptions centrally.
- Never expose stack traces or infrastructure details.

## Validation

- Validate external input at the request DTO boundary with Bean Validation.
- Keep business rules in the application or domain layer.
- Return validation failures through the common error format.

## Collections

- Paginate potentially unbounded collections.
- Use deterministic sorting for pagination.
- Do not load entire tables into memory for API responses.

## OpenAPI

Use springdoc/OpenAPI annotations where they add useful API context.

- Prefer `@Tag` and `@Operation` for API intent.
- Use `@ApiResponse`, `@Parameter`, and `@Schema` only when they add meaningful information.
- Let Bean Validation define request constraints where possible.
- Keep generated OpenAPI documentation consistent with the implemented contract.

For business validation and exception design, follow `IMPLEMENTATION/`.