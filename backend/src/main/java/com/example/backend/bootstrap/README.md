# Application bootstrap

Bootstrap components synchronize version-controlled reference catalogs into the
database. They are not general application configuration and must remain
idempotent.

Set `BOOTSTRAP_CATALOGS_ENABLED=true` only for environments where catalog
synchronization at startup is intended. Flyway remains responsible for schema
changes.
