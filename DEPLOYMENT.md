# Deployment

How InsuredIndex's (insuredindex.inurek.com) two apps get from a merge on GitHub to running containers,
and what the Oracle Cloud deployment needs from this side.

> **Verified locally on 2026-07-05**: both images build and run correctly together (backend +
> frontend + a local MongoDB), including a real *browser* login (not just curl) and an
> authenticated API call proxied through nginx. See "Known gotchas" and "Local verification" below.

## Architecture

```
GitHub (push to main/master)
  → GitHub Actions builds Docker image
  → pushes to GitHub Container Registry (ghcr.io/intraevolve-lms/insuredindex, ghcr.io/intraevolve-lms/insuredindex-fe)

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

`.github/workflows/docker-publish.yml` in **both** repos builds and pushes to **GitHub Container
Registry (ghcr.io)** on every push to `main` or `master` (this repo's current default branch is
`master` — rename it to `main` if you'd rather match convention, the workflow triggers on either).

**No secrets to set up** — the workflow authenticates with the automatic, per-run `GITHUB_TOKEN`
(via `permissions: packages: write` in the workflow itself), not a personal access token. This is
what replaced the earlier Docker Hub setup, which needed `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN`
repo secrets and broke when those credentials went stale.

**One manual, one-time step**: freshly-published GHCR packages default to **private** even from a
public repo. After the first successful push, go to the org's Packages page
(`github.com/orgs/intraevolve-lms/packages`) → `insuredindex` / `insuredindex-fe` → Package settings
→ Change visibility → **Public**. After that, anyone can `docker pull
ghcr.io/intraevolve-lms/insuredindex:latest` with no login at all.

Each push produces three tags: `:latest`, `:<commit-sha>`, and `:v<run-number>` — the last one is
GitHub Actions' own auto-incrementing build counter (1, 2, 3, ...), a human-friendly alternative to
remembering a commit SHA. See "Rolling back a deploy" below.

**Images are multi-arch (`linux/amd64` + `linux/arm64`)**, built via `docker/setup-qemu-action` +
`docker/setup-buildx-action` with `platforms: linux/amd64,linux/arm64`. This matters because Oracle
Cloud's free-tier VMs are commonly **Ampere (ARM64)** shapes — without this, `docker pull` on such a
VM would fetch an amd64-only image, print a `platform does not match` warning, and either run under
slow/flaky QEMU emulation or fail outright, depending on what's installed on the host. The tradeoff:
CI build time goes up noticeably, since the `arm64` leg of the backend build (a full Gradle/JVM
build) runs under QEMU emulation on GitHub's (amd64) runners rather than natively — expect the
backend workflow to take several times longer than before.

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
fine and GETs may still work. It now includes `https://insuredindex.inurek.com`, `https://www.insuredindex.inurek.com`,
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
#   JWT_SECRET=...
#   MONGODB_URI=...
#   BACKEND_TAG=latest
#   FRONTEND_TAG=latest
#   ADMIN_EMAIL / ADMIN_PASSWORD   (first boot only)

docker compose pull
docker compose up -d
```

This is the piece that's the Oracle Cloud teammate's to run/adapt — VM sizing, firewall/security
list rules (80/443 in, plus whatever's needed for SSH), and TLS termination (e.g. a Caddy or
nginx reverse proxy in front with Let's Encrypt, since neither app container currently handles
HTTPS) aren't covered here.

### Rolling back a deploy

Every push builds and publishes a semver tag, `v<major>.<minor>.<patch>`. `major.minor` comes from
that repo's own `VERSION` file (bump it manually, in a commit, when you want a major or minor
release); `patch` auto-increments on every push by scanning existing git tags for the current
`major.minor` prefix, resetting to 0 whenever `VERSION` changes. Check that repo's Tags page, its
Actions tab, or its GHCR "Packages" page to see which version corresponds to which commit/date.
**Backend and frontend version independently** — they're separate repos with separate `VERSION`
files and CI runs, so `v1.2.0` of the backend and `v1.2.0` of the frontend are not necessarily from
the same date or meant to run together. That's why `docker-compose.yml` has two separate tag
variables rather than one shared `TAG`. To roll back, set the relevant one(s) to the last
known-good version and re-pull:

```bash
# .env — roll back just the backend, keep frontend on latest
BACKEND_TAG=v1.1.4
FRONTEND_TAG=latest

docker compose pull
docker compose up -d
```

## Alternative: building on the server instead of pulling from GHCR

The Oracle VM currently builds both images directly on the server from a git clone, rather than
pulling the published GHCR images. That's a supported path, but only if followed exactly —
deviating from it is what caused a real incident: a stale pre-fix image silently kept serving
traffic after the Mongo/CORS bugs were fixed on GitHub, and a hand-edited `Dockerfile` on the
server reintroduced a hardcoded admin password and a `JWT_SECRET` that randomly regenerates on
every restart (silently logging out every user on every deploy). Both were only caught by noticing
the app name in the logs hadn't changed after a "fix."

### One-time setup

```bash
mkdir -p ~/CRM-proj && cd ~/CRM-proj
git clone https://github.com/intraevolve-lms/insurancecrm.git
git clone https://github.com/intraevolve-lms/insurancecrm-fe.git
```

The committed `docker-compose.yml` in this repo pulls from GHCR (see above) — the server needs its
own variant that builds from source instead. **Do not hand-edit `Dockerfile` beyond what's in this
repo, and don't add anything to the compose file's `services:` beyond swapping `image:` for
`build:`.** Any other local edit (hardcoded env defaults, custom entrypoints, etc.) silently drifts
from what's actually tested, and won't survive being overwritten back to the real file later:

```yaml
services:
  backend:
    build:
      context: .
      dockerfile: Dockerfile
    image: insurancecrm:local
    restart: unless-stopped
    environment:
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET must be set}
      MONGODB_URI: ${MONGODB_URI:?MONGODB_URI must be set}
      ADMIN_EMAIL: ${ADMIN_EMAIL:-}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD:-}
    ports:
      - "8081:8081"

  frontend:
    build:
      context: ../insurancecrm-fe
      dockerfile: Dockerfile
    image: insurancecrm-fe:local
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "8080:80"
```

`.env` next to it needs the same variables as the GHCR-pull method above (`JWT_SECRET`,
`MONGODB_URI`, plus `ADMIN_EMAIL`/`ADMIN_PASSWORD` for first boot only). Copy `.env.example` from
this repo as a starting point: `cp .env.example .env`, then fill in the real values.

### Every deploy after that

```bash
cd ~/CRM-proj/insurancecrm && git pull origin master
cd ../insurancecrm-fe && git pull origin master
cd ../insurancecrm
docker compose up -d --build
```

**`--build` is not optional.** The image has a fixed tag (`insurancecrm:local`), so Compose reuses
whatever was built last time if it's left off — `git pull` alone changes nothing that's running.

### Verifying the deploy actually took

```bash
docker ps                                    # both containers: Up, not Restarting
docker logs insurancecrm-backend-1 --tail 50
```

Check for, in this order:
- `[insuredindex]` in the log line prefix, **not** `[insurancecrm]` — proves this is current code,
  not a stale image from before the rebrand (and therefore before the Mongo/CORS fixes too, since
  they landed in the same commit).
- `Monitor thread successfully connected to server` — confirms `MONGODB_URI` is correct and Atlas
  is reachable (check Atlas's Network Access allowlist includes this VM's IP if this line is
  missing and a `Connection refused` to `localhost:27017` shows up instead).
- `Started InsurancecrmApplication in ... seconds` with no exception logged before it.
- `Initial admin account created: <email>` — only appears against a genuinely empty database.

Then actually log in through the browser and confirm the forced password-change screen appears —
that's the real end-to-end proof, not just a clean-looking log.

### Rolling back (build-on-server method)

There's no `:vN` tag to fall back to here, since nothing is pulled from a registry — roll back
with git instead:

```bash
cd ~/CRM-proj/insurancecrm
git log --oneline -10        # find the last known-good commit
git checkout <commit-sha>
docker compose up -d --build
```

Repeat in `insurancecrm-fe` if the frontend also needs rolling back. Switching this VM to pull
published GHCR images instead (see "Running via docker-compose" above) would make rollback as
simple as changing `BACKEND_TAG`/`FRONTEND_TAG` in `.env` — worth doing once this is stable.

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
