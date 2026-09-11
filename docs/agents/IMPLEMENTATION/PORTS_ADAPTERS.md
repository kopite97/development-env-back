# Ports and Adapters

Isolate external systems behind explicit application or domain boundaries.

## Ports

Define a Port as an interface when application behavior depends on an external capability.

Examples:

```text
FileStorage
MailSender
NotificationSender
ExternalProjectClient
GameServerClient
```

- Name ports by the capability required by the application.
- Keep ports independent of vendor-specific SDKs or protocols.
- Define ports in the inner layer that owns the requirement.
- Keep port methods focused on application needs, not complete vendor APIs.

## Adapters

Implement ports in the infrastructure layer.

Example:

```text
Application
     ↓
FileStorage
     ↑
R2FileStorage
     ↓
AWS S3 SDK
```

- Vendor-specific SDKs belong inside adapters.
- Do not expose SDK request, response, or exception types through ports.
- Translate infrastructure failures into application-appropriate exceptions when necessary.
- Keep serialization, authentication, and protocol details inside adapters.

## External Dependencies

Application and domain code must not directly depend on:

```text
R2 / S3 SDK
Mail SDK
External REST clients
Messaging clients
Cloud provider SDKs
```

Access them through an appropriate port.

## Configuration

- Keep credentials and endpoints outside source code.
- Inject deployment-specific configuration into adapters.
- Never hardcode secrets, tokens, or infrastructure addresses.

## Boundaries

Do not create ports for ordinary internal services.

Use ports where they protect the application from external infrastructure or a genuinely replaceable boundary.

When replacing an external provider, prefer changing or adding an adapter without changing application behavior.