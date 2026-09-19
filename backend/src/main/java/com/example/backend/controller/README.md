# Controller packages

Controllers are grouped by domain. Moving a controller does not change its API
URL because routes remain defined by Spring annotations.

| Package | Responsibility |
|---|---|
| `admin` | Platform administration |
| `assignment` | Assignments and student activity |
| `auth` | Login and account authentication |
| `curriculum` | Read-only curriculum APIs |
| `export` | Simulation data export |
| `library` | Reusable simulation library and folders |
| `problem` | Problem ingestion and specifications |
| `reviewer` | Review, moderation, schemas, and evaluation |
| `school` | Schools, classes, reports, and payments |
| `simulation` | Simulation execution and WebSocket messages |
| `support` | User support requests |

Controllers handle HTTP/WebSocket transport, input validation, and endpoint
authorization only. Business rules belong in services, and persistence access
belongs behind services and repositories.
