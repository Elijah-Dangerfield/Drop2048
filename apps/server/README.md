# `:apps:server` — Ktor backend

A small, opinionated Ktor + Postgres backend. It mirrors
the client's conventions (kotlin-inject DI, `domain/`↔`data` split, one blessed
way to do each thing) so moving between client and server is the same mental
model. This README is the detailed reference; keep `AGENTS.md` minimal and point
here.

## Quick start

The server degrades gracefully, so you can run it with **zero config**:

```bash
./gradlew :apps:server:run            # boots in "limited mode": /_health + /v1/example
curl localhost:8080/_health           # {"ok":true}
curl localhost:8080/v1/example        # {"message":"…","items":[…]}
```

To enable the database-backed routes, set env vars (copy
`apps/server/.env.example` → `apps/server/.env`, which is gitignored):

1. **Database** — start the bundled Postgres and point at it:
   ```bash
   docker compose -f apps/server/docker-compose.yml up -d
   # in apps/server/.env:
   DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres
   ```
   On boot, Flyway applies everything in `src/main/resources/db/migration`.

There are no accounts and no authenticated user routes — the app this server
backs has neither (`docs/SPEC.md` 20). The token-gated config admin API under
`/v1/admin/config` is machine-to-machine, keyed on `ADMIN_API_TOKEN`.

Boot modes (graceful degradation is a deliberate standard):

| `DATABASE_URL` | What's served |
|---|---|
| unset | `/_health`, `/v1/example` |
| set   | + `/v1/config` and, with `ADMIN_API_TOKEN`, the config admin API |

## Layout

```
config/      Env (the only env reader) + ServerConfig (typed, parsed once)
di/          ServerScope + ServerComponent (kotlin-inject + anvil)
db/          Database (Hikari + Flyway + Exposed), Tables, TimeConversions
domain/      interfaces + models + sealed outcomes (no framework imports)
data/        impls, prefixed by backing store (InMemory*, Postgres*)
plugins/     Ktor plugins: Serialization, Errors, Cors, Observability
routes/      one `fun Route.xRoutes(deps)` per resource + its DTO file
resources/db/migration/   Flyway V<n>__snake.sql (source of truth for the schema)
```

`Main.kt` parses config → `Application.module(config)` does production-only setup
(observability, DB connect, DI graph) → `installApp(component)` installs the
functional plugins + mounts routes. `installApp` is the seam the
`:apps:integration` harness reuses (real graph, real DB).

## Conventions (copy these)

### Add a config value — `config/ServerConfig.kt`
`Env` is the only place env vars are read. Pick by criticality:
- boot-critical → `env.require("KEY")` (fail fast)
- optional w/ default → `env.int("KEY", 8080)` / `env["KEY"] ?: "x"`
- optional, degrades → nullable `env["KEY"]`, branch at the call site

Group related vars into a `data class XxxConfig` with a `fromEnv(env)` companion.
Document the var in `.env.example`.

### Add a service — `domain/` interface + `data/` impl
```kotlin
// domain/Thing.kt
interface ThingRepository { suspend fun get(): Thing }

// data/PostgresThingRepository.kt
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresThingRepository(private val database: Database, private val clock: Clock) : ThingRepository
```
Then expose it on `ServerComponent` as `abstract val thingRepository: ThingRepository`.
anvil + KSP wire the rest. The impl prefix names the backing store
(`InMemory*`, `Postgres*`, `Http*`).

### Add a route — `routes/XxxRoutes.kt` + `XxxDto.kt`
```kotlin
fun Route.thingRoutes(repo: ThingRepository) {
    get("/v1/thing") {
        call.respond(repo.get().toResponse())  // respond with a DTO, never a domain type
    }
}
```
- real paths are versioned under `/v1`; `/_health` is the deliberate exception.
- DTOs live in `XxxDto.kt`, named `*Response` / `*Request`; map with `Thing.toResponse()`.
- map domain outcomes to status codes with an exhaustive `when`; errors use the
  one `ProblemResponse` envelope (`call.respond(status, problem("code", "msg"))`).
- mount it in `installApp`.

### Add a migration — `resources/db/migration/V<n>__snake.sql`
Flyway SQL is the **source of truth** for the schema; the Exposed objects in
`db/Tables.kt` are read-side projections. Never edit an applied migration — add
the next one. Mirror schema changes into `Tables.kt` and add a line to
`DatabaseSchemaTest`. Repositories run every method in `database.transaction { }`,
take an injected `Clock`, and treat a unique-violation (SQLSTATE `23505`) as the
arbiter rather than pre-checking.

## Testing

Two patterns, each with a copyable example:
- **Route test** (`routes/ExampleRoutesTest.kt`, `routes/ConfigAdminRoutesTest.kt`)
  — `testApplication` + the real plugins + a fake passed as a plain arg.
- **Repository test** (`data/PostgresAppConfigSourceTest.kt`) — real Postgres
  via Testcontainers (`DatabaseTest`), `@After` table cleanup, injected clock.
  Skips cleanly (JUnit `Assume`) when Docker is absent.

The real DI graph over a real server and a real client is `:apps:integration`
(`HarnessSmokeTest`), not a server-side test.

```bash
./gradlew :apps:server:test           # add -Ddrop2048.skipGitHooksCheck=true outside a hooked checkout
```

## Environment variables

| Var | Required | Default | Notes |
|---|---|---|---|
| `DATABASE_URL` | no | — | `postgresql://user:pass@host:port/db`. Unset → limited mode. URL-encode `$`→`%24`. |
| `DATABASE_POOL_MAX_SIZE` | no | 10 | |
| `DATABASE_POOL_MIN_IDLE` | no | 2 | |
| `SERVER_HOST` | no | `0.0.0.0` | |
| `SERVER_PORT` | no | 8080 | |


