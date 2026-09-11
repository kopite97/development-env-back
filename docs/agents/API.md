# API

Design APIs as stable external contracts.
Do not expose persistence or domain implementation details directly.

## Endpoint Design

Use resource-oriented REST endpoints.

Prefer:

```text
GET    /api/projects
GET    /api/projects/{id}
POST   /api/projects
PUT    /api/projects/{id}
DELETE /api/projects/{id}
```

- Use nouns for resource paths.
- Use HTTP methods to express operations.
- Avoid action verbs in URLs unless the operation cannot be represented naturally as a resource change.
- Use consistent naming across all endpoints.

## DTO

- Never expose JPA entities directly through APIs.
- Use separate request and response DTOs.
- Do not reuse request DTOs as response DTOs.
- DTOs must not contain business logic.
- Prefer purpose-specific names such as:

```text
CreateProjectRequest
UpdateProjectRequest
ProjectResponse
ProjectSummaryResponse
```

## Response

Normal JSON responses must use:

```java
ResponseEntity<ApiResponse<T>>
```

Use one consistent response model across the API.

Example:

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

Errors must use the shared error response format.

Exceptions to the common wrapper are allowed for cases such as:

- `204 No Content`
- file downloads
- streaming responses

## Status Codes

Use HTTP status codes according to their semantics.

- `200` — successful read or update
- `201` — resource created
- `204` — successful operation without response body
- `400` — invalid request
- `401` — unauthenticated
- `403` — unauthorized
- `404` — resource not found
- `409` — state or resource conflict

## Validation

- Validate external input at the request DTO boundary.
- Use Bean Validation for structural validation.
- Do not place business rules in request DTOs.
- Return validation failures using the shared error format.

## Collections

- APIs returning potentially unbounded collections must support pagination.
- Define deterministic sorting for paginated responses.
- Do not load entire tables into memory for API responses.

## Errors

- Do not build error responses manually in individual controllers.
- Handle expected application and domain exceptions centrally.
- Do not expose internal stack traces or infrastructure details to clients.

For business validation and exception design, follow `IMPLEMENTATION/`.