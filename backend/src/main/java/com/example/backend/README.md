# Backend package structure

The backend uses layered packages, with every business layer grouped by the
same domain name. Keep HTTP contracts and persistence details out of services.

```text
com.example.backend
├── controller/<domain>   REST and WebSocket entry points
├── dto/<domain>          request and response contracts
├── service/<domain>      use cases and transaction boundaries
├── repository/<domain>   Spring Data persistence ports
├── entity/<domain>       JPA entities and relationships
├── entity/enums          persisted domain enums
├── ai                    external AI integration and structured extraction
├── physics               deterministic solvers and validation
├── config                environment-backed framework configuration
├── security              authentication and authorization infrastructure
└── exception             API error translation
```

## Placement rules

1. Use the same domain folder across controller, DTO, service, repository, and
   entity layers (`problem`, `simulation`, `school`, and so on).
2. Controllers depend on services; they do not call repositories directly.
3. Services depend on repositories and provider interfaces, not concrete
   provider-neutral OpenAI-compatible implementations.
4. Repositories only expose persistence queries and never contain use-case
   logic.
5. JPA entities do not depend on controllers, DTOs, services, or repositories.
6. Runtime settings come from typed classes in `config` backed by environment
   variables. Prompt text belongs in `src/main/resources/prompts`.
7. Add a top-level package only when its responsibility does not fit an
   existing package.

## Domain vocabulary

- `account`: users, roles, and current-user access.
- `assignment`: teacher assignments and student submissions.
- `curriculum`: grade levels, modules, topics, and lessons.
- `evaluation`: benchmark and extraction-quality evaluation.
- `library`: reusable simulations and moderation.
- `problem`: source assets, extraction runs, specifications, and ambiguities.
- `reviewer`: reviewer decisions and review workflows.
- `school`: schools, classes, enrollment, licenses, and payments.
- `simulation`: simulations, immutable runs, solvers, and validation.
- `support`: support requests.
