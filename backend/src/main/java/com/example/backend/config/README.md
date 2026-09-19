# Backend configuration

This package contains wiring only. Runtime workflows and database writes do not
belong here.

- `database`: datasource and connection-pool wiring.
- `http`: shared outbound HTTP clients.
- `properties`: validated, typed environment-backed settings.
- `web`: HTTP documentation and WebSocket wiring.

Startup data initialization lives in `com.example.backend.bootstrap` and is
enabled explicitly with `BOOTSTRAP_CATALOGS_ENABLED`.
