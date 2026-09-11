# Security

Use Spring Security as the security framework.
Prefer secure defaults and expose only explicitly permitted access.

## Authentication

Browser users use session-based authentication.

- Use OAuth2/OIDC providers such as Google or GitHub for login.
- Do not implement local password authentication unless explicitly required.
- Keep authenticated user state in the server session.
- Use secure session cookies with `HttpOnly`, `Secure`, and an appropriate `SameSite` policy.
- Do not store authentication credentials or session identifiers in browser storage.

## Public Access

Require authentication by default.

Explicitly permit only endpoints that must be public, such as:

```text
OAuth2 login endpoints
OAuth2 callback endpoints
Health endpoints when required
Public static resources
```

Do not make new API endpoints public implicitly.

## Authorization

Authentication alone is not sufficient.

- Enforce role and permission requirements where applicable.
- Validate resource ownership for user-owned data.
- Never trust resource identifiers supplied by the client as proof of ownership.
- Keep authorization rules consistent across application use cases.

## External Integrations

External provider authentication is independent from user authentication.

Examples:

```text
GitHub      → GitHub OAuth/App Token
Cloudflare  → Cloudflare API Token
Render      → Render API Credential
```

- Keep provider credentials in the backend.
- Never expose provider secrets to the frontend.
- Access external providers through the appropriate Port/Adapter boundary.

## JWT

Do not introduce JWT authentication by default.

Use JWT only when a non-browser client or service API explicitly requires bearer-token authentication, such as a Unity game client or external service.

Do not replace browser session authentication with JWT solely because external provider APIs are integrated.

## CSRF and CORS

- Keep CSRF protection enabled for session-based browser authentication unless a reviewed design requires otherwise.
- Allow CORS only for explicitly approved origins.
- Do not use unrestricted CORS in production.

## Secrets

- Never hardcode passwords, tokens, client secrets, or API credentials.
- Use environment variables or an approved secret store.
- Never write credentials or authentication tokens to logs.