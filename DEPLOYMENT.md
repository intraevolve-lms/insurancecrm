# Deployment

How InsuredIndex's (insuredindex.com) two apps get from a merge on GitHub to running containers,
and what the Oracle Cloud deployment needs from this side.

> **Verified locally on 2026-07-05**: both images build and run correctly together (backend +
> frontend + a local MongoDB), including a real *browser* login (not just curl) and an
> authenticated API call proxied through nginx. See "Known gotchas" and "Local verification" below.

## Architecture

```
GitHub (push to main/master)
  → GitHub Actions builds Docker image
  → pushes to Docker Hub (nawaz027/insuredindex, nawaz027/insuredindex-fe)

Oracle Cloud VM
  → docker compose pull && docker compose up -d
  → frontend (nginx, port 80) ──proxies /api/*──→ backend (Spring Boot, port 8081)
                                                         │
                                                         ▼
                                              MongoDB Atlas (free M0 cluster)
```

The frontend's built JS always calls a **relative** `/api/...` path (see `src/lib/axios.ts`) —
there's no build-time backend URL to configure. That means nginx (in the frontend container)
must reverse-proxy `/api/` to the backend container — already set up in `insurancecrm-fe/nginx.conf`,
proxying to a service literally named `backend` (matching the `docker-compose.yml` service name).
If the Oracle setup runs these containers under different service names or on different hosts,
that proxy target needs updating to match.

## Images

- **Backend** (`Dockerfile`, this repo): multi-stage — `eclipse-temurin:21-jdk-jammy` runs
  `./gradlew bootJar -x test` (tests are skipped here since there's no MongoDB reachable during
  the image build — CI runs the full test suite separately, before this step), then the jar is
  copied into a slim `eclipse-temurin:21-jre-jammy` runtime image, running as a non-root user.
- **Frontend** (`Dockerfile`, in `insurancecrm-fe`): multi-stage — `node:20-alpine` runs
  `npm ci && npm run build`, then the static `dist/` output is served by `nginx:1.27-alpine`.

## CI/CD (GitHub Actions)

`.github/workflows/docker-publish.yml` in **both** repos builds and pushes to Docker Hub on every
push to `main` or `master` (this repo's current default branch is `master` — rename it to `main`
if you'd rather match convention, the workflow triggers on either).

**One-time setup**, in each repo's GitHub Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_TOKEN` | A Docker Hub [access token](https://app.docker.com/settings/personal-access-tokens) (not your password) — needs Read & Write |

Each push produces two tags: `:latest` and `:<commit-sha>` (so you can pin a specific version in
`docker-compose.yml` via `TAG=<sha>` if `:latest` ever needs rolling back).

## MongoDB Atlas setup

1. Create a free **M0** cluster (512MB storage, shared RAM/vCPU, 3-node replica set included).
2. **Network Access** → add the Oracle VM's public IP (M0 has no VPC peering, so this is the
   only way in).
3. **Database Access** → create a user with read/write on the `insuredindex` database.
4. Copy the connection string (`mongodb+srv://...`) — this is `MONGODB_URI`.

### Backups

M0's replication protects against a node dying, but a bad bulk-update or a buggy migration
replicates everywhere just as fast — Atlas doesn't offer point-in-time restore below the paid
M10 tier. `scripts/backup-mongo.sh` runs `mongodump` via Docker (no local install needed) and
prunes anything older than `RETENTION_DAYS`. Wire it up as a cron job on the VM:

```
0 2 * * * MONGODB_URI="mongodb+srv://..." BACKUP_DIR="/opt/insuredindex/backups" /opt/insuredindex/scripts/backup-mongo.sh >> /var/log/insuredindex-backup.log 2>&1
```

The script leaves an "upload elsewhere" step unconfigured at the bottom — backups sitting only on
the same VM they're backing up won't survive that VM being lost. Point it at OCI Object Storage
(or wherever) once that's decided.

## Required environment variables (backend container)

| Variable | Required | Notes |
|---|---|---|
| `JWT_SECRET` | **Yes** | 32+ random characters. The app refuses to start without it — there is no baked-in default since the source is public. Generate with `openssl rand -base64 48`. |
| `MONGODB_URI` | **Yes** | Full Atlas connection string, including the database name. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Only for first boot | Seeds the initial admin account when the `users` collection is empty (8+ char password). Safe to remove from the environment after that first successful boot — see "First admin account" below. |

## Known gotchas

### Spring Boot 4.1 Mongo property prefix

This project is on Spring Boot **4.1.0**, which modularized the Mongo auto-configuration and
renamed the connection-string property from the old `spring.data.mongodb.uri` to
**`spring.mongodb.uri`** (`spring.data.mongodb.*` now only covers Spring Data-level settings like
`auto-index-creation`). `application.yaml` already uses the correct prefix — but if you ever add
more Mongo-related properties, double-check which prefix they belong under, since the wrong one
is silently ignored (no startup error) and the app quietly falls back to `localhost:27017`. This
exact bug shipped once already and would have broken the Atlas connection in production; caught
by the local verification run below.

### CORS allowed origins must include the real domain

`CorsConfig.java` allowlists exact origins — it shipped with only `localhost:3000`/`localhost:5173`
(the Vite dev server ports). The browser sends an `Origin` header on every API POST/PUT/PATCH/DELETE
regardless of whether nginx is reverse-proxying same-origin underneath, and Spring's CORS filter
checks it strictly against this list — so without the real domain in it, **every login and every
write request in production would fail with `403 Invalid CORS request`**, even though the app looks
fine and GETs may still work. It now includes `https://insuredindex.com`, `https://www.insuredindex.com`,
and `http://localhost:8080` (for the local Docker verification flow below). If the production domain
ever changes, or you add a `www.`/staging subdomain, update this list and rebuild — caught by testing
a real browser login (not just curl) against the local Docker stack, see below.

## First admin account

There's no public signup — every `/api/users/**` endpoint requires an existing `ADMIN`, so the
very first admin has to come from somewhere other than the app itself. `DataInitializer` seeds one
on startup **only if the `users` collection is empty and both `ADMIN_EMAIL`/`ADMIN_PASSWORD` are
set** (8+ char password); if either is missing, it just logs a warning and creates nothing — there
is no hardcoded fallback account to accidentally ship.

Set `ADMIN_EMAIL`/`ADMIN_PASSWORD` for the first deploy only, hand those credentials to whoever
that first admin is through a secure channel (not plaintext email/chat), then remove the two env
vars — they're a one-time bootstrap, not something that should stay configured. That seeded account
has `mustChangePassword` set, so the frontend forces a password change (`POST
/api/auth/change-password`) right after its first login, before it can reach the rest of the app.

## Running via docker-compose

`docker-compose.yml` in this repo pulls the published images (doesn't rebuild from source):

```bash
# .env alongside docker-compose.yml:
#   DOCKERHUB_USERNAME=nawaz027
#   JWT_SECRET=...
#   MONGODB_URI=...
#   TAG=latest

docker compose pull
docker compose up -d
```

This is the piece that's the Oracle Cloud teammate's to run/adapt — VM sizing, firewall/security
list rules (80/443 in, plus whatever's needed for SSH), and TLS termination (e.g. a Caddy or
nginx reverse proxy in front with Let's Encrypt, since neither app container currently handles
HTTPS) aren't covered here.

## Verifying a build locally

`docker-compose.local.yml` builds both images from source and runs them with a throwaway local
MongoDB — no Atlas or Docker Hub needed. Assumes `insurancecrm` and `insurancecrm-fe` are checked
out as sibling directories (edit the frontend build path in the file if not).

```bash
docker compose -f docker-compose.local.yml up -d --build
docker compose -f docker-compose.local.yml logs -f backend   # watch for "Started InsurancecrmApplication"

# Frontend (proxies /api to the backend):
curl http://localhost:8080/

# Backend directly (ADMIN_EMAIL/ADMIN_PASSWORD as set in docker-compose.local.yml):
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@local.test","password":"local-admin-password"}'

docker compose -f docker-compose.local.yml down -v   # tear down when done
```
