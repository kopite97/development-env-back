# Spring Boot

Follow Spring Boot conventions and prefer framework-standard solutions over custom infrastructure.

## Dependency Injection

- Use constructor injection.
- Do not use field injection.
- Prefer immutable dependencies declared as `final`.
- Use Lombok `@RequiredArgsConstructor` when it improves readability.
- Do not create interfaces only to support dependency injection.

## Components

Use Spring stereotypes according to responsibility:

- `@RestController` — HTTP endpoints
- `@Service` — application services
- `@Repository` — persistence implementations when required
- `@Configuration` — framework configuration
- `@Component` — only when no more specific stereotype applies

Do not use Spring components as global utility containers.

## Configuration

- Keep environment-specific values outside source code.
- Use `application.yml` for application configuration.
- Use environment variables for secrets and deployment-specific values.
- Prefer `@ConfigurationProperties` for grouped configuration.
- Do not hardcode URLs, credentials, tokens, or infrastructure settings.

## Profiles

Use Spring profiles only when behavior or configuration genuinely differs by environment.

Typical profiles may include:

```text
local
test
prod
```

Do not duplicate large configuration blocks across profiles without need.

## Framework Usage

- Prefer Spring-supported abstractions before introducing custom implementations.
- Avoid static access to Spring-managed dependencies.
- Do not access the `ApplicationContext` directly for normal dependency resolution.
- Do not introduce new Spring modules or third-party dependencies without a concrete requirement.

## Runtime

- Application behavior must be configurable through environment variables where deployment requires it.
- Log to stdout/stderr in container environments.
- Keep the application container stateless.

For API rules, see `API.md`.
For persistence rules, see `DATABASE.md`.
For implementation design, see `IMPLEMENTATION/AGENTS.md`.