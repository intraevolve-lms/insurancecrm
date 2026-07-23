# InsuredIndex — Backend

Spring Boot 4 / MongoDB backend for InsuredIndex (insuredindex.inurek.com), an insurance agency CRM: customers, communication logging, agent performance, and renewal reminders. Role-based access for `ADMIN` and `AGENT` users, secured with JWT.

## Prerequisites

- Java 21
- MongoDB running locally (or reachable via `MONGODB_URI`) — default: `mongodb://localhost:27017/test`

## Required environment variables

| Variable | Required | Default | Notes |
|---|---|---|---|
| `JWT_SECRET` | **Yes** | — | HMAC signing key for JWTs, 32+ characters. The app **fails to start** without this — there is deliberately no baked-in default, since this repo is public. Generate one yourself, e.g. `openssl rand -base64 48`. |
| `MONGODB_URI` | No | `mongodb://localhost:27017/test` | Full Mongo connection string, including the database name. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | No, but see below | — | Seeds the very first admin account. Only used once, when the `users` collection is empty — see "First admin account" below. |

Set them however suits your setup — a shell export, an IDE run configuration, or a `.env`-style tool of your choice (not read automatically by this app).

## Running locally

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_EMAIL="you@example.com"
export ADMIN_PASSWORD="$(openssl rand -base64 18)"
./gradlew bootRun
```

The app starts on **http://localhost:8081**.

### First admin account

There's no public signup — every `/api/users/**` endpoint requires an existing `ADMIN`, so the
very first admin has to be provisioned from outside the app. On startup, if the `users` collection
is empty, `DataInitializer` seeds one **only if both `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set**
(`ADMIN_PASSWORD` must be 8+ characters); if either is missing, it logs a warning and creates
nothing. That seeded account has `mustChangePassword` set, so its first `/api/auth/login` response
carries `mustChangePassword: true` — the frontend should route straight to a forced password-change
screen (`POST /api/auth/change-password`, requires the current password) before letting them into
the app. There is no hardcoded fallback account — if you don't set these env vars before the first
boot, create the admin manually (e.g. insert a document into `users` directly, with a bcrypt-hashed
password).

API docs (Swagger UI): `http://localhost:8081/swagger-ui.html`

## Running tests

```bash
./gradlew test
```

Unit tests use Mockito (no external dependencies). Integration tests under `src/test/java/.../controller` need a local MongoDB instance and use a separate database (`insuredindex_test`, configured in `src/test/resources/application.yaml`) so they never touch your development data.

## Tech stack

Spring Boot 4, Spring Security (JWT), Spring Data MongoDB, Apache POI (Excel import/export), Apache Commons CSV, springdoc-openapi.
