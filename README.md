# ReviewLens 2.0 — Aspect-Based Sentiment & Voice-of-Customer Analytics

ReviewLens analyses Amazon product reviews with **aspect-based sentiment analysis (ABSA)**: instead of labelling a
whole review positive or negative, it finds each product feature mentioned (camera, battery, display, price,
performance, build quality …) and the sentiment expressed about it. Use case **DSCI-12**.

> *“The camera quality is excellent, but the battery drains quickly. The display is beautiful, although the phone is overpriced.”*
> → Camera **positive** · Battery **negative** · Display **positive** · Price **negative**

## Architecture

```
Browser ── React 18 + Vite (:5173) ──REST──> Spring Boot 4 (:8080) ──Spring Data──> MongoDB (local or Atlas)
                                                    │
                                                    └──HTTP──> Python FastAPI NLP engine (:8000)
```

React **never** talks to MongoDB — every request goes through the Spring Boot REST API, which owns authentication,
authorisation, persistence and the analysis job queue. The Python service only does NLP.

| Folder | Stack | Responsibility |
|---|---|---|
| `frontend/` | React 18, Vite, Tailwind CSS 4, Framer Motion, Recharts, Lucide | Landing page, auth, user workspace (8 pages), admin portal (10 pages) |
| `backend/` | Spring Boot 4.2, Java 25, Spring Security (JWT), Spring Data MongoDB, Commons CSV, OpenPDF | REST API, roles, job queue, analytics aggregations, reports, audit log |
| `nlp-engine/` | Python, FastAPI, VADER | Pre-processing, aspect extraction, aspect sentiment, keywords (rule-based baseline) |
| `database/` | — | MySQL → MongoDB migration script |
| `scripts/` | Python | End-to-end smoke test of the running stack |
| `legacy/` | — | The v1 (MySQL/H2, plain CSS) sources, kept for reference; not built |

## Quick start

Prerequisites: **Node 20+**, **Python 3.11+**, **JDK 25 (LTS)** (selected via `JAVA_HOME`/`PATH`), **MongoDB 6+** (local) or a MongoDB Atlas cluster. Maven is not needed (`mvnw` is included).

```powershell
cd ReviewLens
.\start-all.ps1        # creates .env (random JWT secret) on first run, then starts all three services
```

Open **http://localhost:5173**. Upload `sample-data/sample_electronics_reviews.csv` (450 **synthetic** reviews for smoke tests) or a public dataset.

<details><summary>Start the services manually</summary>

```powershell
# NLP engine
cd nlp-engine; python -m venv .venv; .\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\python -m uvicorn app:app --port 8000
# Backend (reads ..\.env automatically)
cd backend; .\mvnw.cmd spring-boot:run
# Frontend
cd frontend; npm install; npm run dev
```
</details>

## MongoDB setup

**Local MongoDB** — install MongoDB Community Server and make sure the service is running (Windows: *Services → MongoDB*). The default
`MONGODB_URI=mongodb://localhost:27017/…` and database `reviewlens` need no further configuration. Collections and indexes are created
automatically on first start.

**MongoDB Atlas**
1. Create a cluster, a database user with *readWrite* on the `reviewlens` database, and allow your IP under *Network Access*.
2. Copy the connection string and put it in `.env` (never in source code):
   `MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority&serverSelectionTimeoutMS=5000`
3. Restart the backend.

### Collections

| Collection | Contents | Key indexes |
|---|---|---|
| `users` | accounts (BCrypt hash, role, active, tokenVersion, lastLoginAt) | unique `email`, `{role, active}`, `createdAt` |
| `datasets` | uploaded files, column mapping, status, counts, date span | `{ownerId, createdAt}`, `{status, createdAt}` |
| `products` | per-dataset products with denormalised review statistics | `{datasetId, reviewCount}`, `ownerId` |
| `reviews` | review text, overall sentiment, **embedded bounded `aspects` array**, quality flags | `{datasetId, productId, reviewDate}`, `{datasetId, aspects.aspect, aspects.sentiment}`, `{datasetId, textHash}`, `qualityFlags` |
| `aspectResults` | one document per (review, aspect) — source of all aggregations | `{datasetId, aspect, sentiment}`, `{datasetId, productId, aspect}`, `{datasetId, reviewMonth}`, `reviewId` |
| `analysisJobs` | queued / running / completed / failed analysis runs with duration and engine label | `{status, createdAt}`, `{datasetId, createdAt}`, `finishedAt` |
| `keywords` | cached keyword / praise / concern terms per dataset or product | `{datasetId, productId, type}` |
| `reports` | report library (the PDF/CSV file is stored on disk in `REPORTS_DIR`) | `{ownerId, createdAt}` |
| `auditLogs` | append-only administrative audit trail | `timestamp`, `{action, timestamp}`, `{actorId, timestamp}` |
| `systemSettings` | single `global` document of admin-managed settings | — |

References are stored as **ObjectIds**. Processing uses **bulk inserts / bulk updates**, streams reviews in batches (never a whole dataset
in memory), and every list endpoint is **paginated**. Analytics are MongoDB aggregation pipelines.

### Migrating existing MySQL data

Only needed if you used ReviewLens v1 with MySQL. `database/migrate_mysql_to_mongo.py` copies users (keeping their password hashes) and
re-queues each dataset for analysis from its original uploaded CSV. It is a **dry run by default**:

```powershell
pip install pymysql pymongo
python database/migrate_mysql_to_mongo.py --mysql-user root --mysql-password <pw> --uploads backend/uploads            # preview
python database/migrate_mysql_to_mongo.py --mysql-user root --mysql-password <pw> --uploads backend/uploads --execute  # write
```

> No MySQL data has been migrated in this repository: during development v1 ran on the embedded H2 database with synthetic sample data only.
> The script was syntax-checked but has not been run against a live MySQL server.

## Configuration (`.env`, git-ignored — see `.env.example`)

| Variable | Purpose |
|---|---|
| `MONGODB_URI`, `MONGODB_DATABASE` | MongoDB connection (local or Atlas) |
| `JWT_SECRET` | ≥ 32 random characters. If empty a random per-process secret is used (sessions end on restart) |
| `JWT_EXPIRATION_HOURS`, `JWT_ADMIN_EXPIRATION_HOURS` | token lifetime for users (24 h) and admin-portal sign-ins (8 h) |
| `NLP_SERVICE_URL` | Python NLP engine (default `http://localhost:8000`) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | first administrator (see below) |
| `UPLOAD_DIR`, `REPORTS_DIR`, `MAX_REVIEWS`, `CORS_ORIGINS` | optional |

## Administrator setup

There is **no public admin registration**. The first administrator is created from the environment:

1. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` (minimum 12 characters) in `.env`.
2. Start (or restart) the backend. If **no administrator exists yet**, the account is created (or an existing account with that email is
   promoted, keeping its own password). The event is written to the audit log. Once any administrator exists the variables are ignored,
   so they can never be used to escalate privileges later. You may remove `ADMIN_PASSWORD` from `.env` afterwards.
3. Sign in at **http://localhost:5173/admin/login**. Further administrators are promoted from *Admin → Users*.

### Roles and permissions

| Capability | USER | ADMIN |
|---|:-:|:-:|
| Upload datasets, dashboards, aspect analysis, comparison, review explorer, analyzer, reports (own data only) | ✓ | ✓ |
| Admin portal and every `/api/admin/**` endpoint | — | ✓ |
| View all users / datasets / products / reviews / jobs | — | ✓ |
| Activate / deactivate accounts, change roles | — | ✓ |
| Delete any dataset, product or review; retry jobs | — | ✓ |
| Read and export audit logs, change system settings | — | ✓ |

Security model: the role is **always read from MongoDB** on each request (never from the client or the token); `/api/admin/**` is protected
by a URL rule **and** `@PreAuthorize("hasRole('ADMIN')")`; deactivating a user, changing a role or signing out increments the user's
token version so existing tokens stop working immediately; the last active administrator cannot be demoted or deactivated; sign-in is
rate-limited (10 failures / 15 min) with timing-safe password checks; API responses and logs never contain password hashes, tokens or
connection strings. For production, give the application a MongoDB user without `remove/update` rights on `auditLogs`.

## Admin portal (`/admin/*`)

| Page | What it does |
|---|---|
| Overview | users (active/inactive), datasets, products, reviews, pending/failed jobs, daily/weekly/monthly usage, processing statistics, live service health, recent activity |
| Users | search, role/status filters, profile with usage and audit trail, activate/deactivate, promote/demote (confirmed, audited) |
| Datasets | every upload with owner, size, status and errors; retry; cascade delete with typed confirmation |
| Products | all products with review counts and aspect sentiment; audited deletion |
| Reviews | duplicate / too-short / no-rating / no-aspect detection, search, single and bulk moderation |
| Analysis jobs | queued / running / completed / failed jobs, progress and duration, safe retry, API / MongoDB / NLP health |
| Platform analytics | user growth, uploads, review volume, success rate, top categories, weekday × hour usage; CSV and PDF export |
| Audit logs | filterable, read-only trail with metadata; CSV export |
| System settings | upload size, reviews per dataset, per-user limits, formats, concurrent jobs, feature flags, maintenance mode (validated server-side; dangerous changes need confirmation) |

## The user workspace

Landing page with a live demo (real NLP engine, rate-limited) · sign-in / registration with validation · **Dashboard** (KPIs, generated
insight, aspect sentiment, distribution, ratings, trends, concerns, praise, keywords, products, dataset and date filters) · **Datasets**
(drag-and-drop, server-side preview, column mapping, progress, job history) · **Aspect analysis** (per-aspect split, trend, wording,
products, example opinions) · **Compare** (2–4 products, strengths/weaknesses, scorecard) · **Review explorer** (search, aspect /
sentiment / rating filters, highlighting, detail drawer) · **Live analyzer** · **Reports** library (PDF / CSV, background generation).
Light, charcoal-dark and system themes; keyboard command menu (Ctrl/⌘ K); responsive down to phone width.

## NLP engine (baseline)

Cleaning → clause segmentation on contrast words → aspect lexicon (13 aspects, ~200 terms) → VADER sentiment extended with electronics
domain rules → per-aspect aggregation. Every result is labelled with the engine (`rule-based-vader 1.1.0`), stored on each analysis job
and dataset and shown in the UI, so baseline results can always be distinguished from a future transformer model. “Signal strength”
(mean |score|) is shown instead of a confidence because the rule-based baseline has no calibrated confidence.

## API overview

All endpoints except `/api/auth/register|login|admin/login`, `/api/health` and `/api/public/**` require `Authorization: Bearer <token>`.
IDs are MongoDB ObjectId strings.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/admin/login`, `POST /api/auth/logout`, `GET /api/auth/me` |
| Public | `GET /api/health`, `GET /api/public/status`, `POST /api/public/demo-analyze` |
| Datasets | `POST /api/datasets` (multipart `file`, `name`, JSON part `mapping`), `POST /api/datasets/preview`, `GET /api/datasets`, `GET /api/datasets/{id}`, `GET /api/datasets/{id}/jobs`, `POST /api/datasets/{id}/reprocess`, `DELETE /api/datasets/{id}`, `GET /api/datasets/{id}/products`, `GET /api/datasets/{id}/reviews?productId&aspect&sentiment&rating&q&sort&page&size` |
| Analytics | `GET /api/analytics/dashboard`, `/compare`, `/aspects`, `/aspects/detail` (all accept `from`/`to`), `POST /api/analyze` |
| Reports | `GET/POST /api/reports`, `GET /api/reports/{id}`, `GET /api/reports/{id}/download`, `DELETE /api/reports/{id}`; v1 direct downloads `GET /api/reports/{datasetId}/pdf|csv` |
| Admin | `GET /api/admin/dashboard`, `GET /api/admin/system/health`, `GET /api/admin/users`, `GET /api/admin/users/{id}`, `PATCH /api/admin/users/{id}/status`, `PATCH /api/admin/users/{id}/role`, `GET/DELETE /api/admin/datasets[/{id}]`, `POST /api/admin/datasets/{id}/retry`, `GET/DELETE /api/admin/products[/{id}]`, `GET /api/admin/reviews`, `GET /api/admin/reviews/summary`, `DELETE /api/admin/reviews/{id}`, `POST /api/admin/reviews/bulk-delete`, `GET /api/admin/analysis-jobs`, `GET /api/admin/analysis-jobs/stats`, `POST /api/admin/analysis-jobs/{id}/retry`, `GET /api/admin/analytics|reports?range=30d|12w|12m`, `GET /api/admin/reports/export?format=pdf|csv`, `GET /api/admin/audit-logs`, `GET /api/admin/audit-logs/actions`, `GET /api/admin/audit-logs/export`, `GET/PUT /api/admin/settings` |

Errors use one JSON shape: `{ "status", "code", "message", "timestamp" }` (e.g. `ACCOUNT_DISABLED`, `LAST_ADMIN`, `CONFIRMATION_REQUIRED`, `MAINTENANCE`).

## Tests

```powershell
cd backend;     .\mvnw.cmd test          # 10 security/authorisation integration tests (needs MongoDB on localhost; uses database reviewlens_test)
cd nlp-engine;  .\.venv\Scripts\python -m pytest -q   # ABSA engine unit tests
cd frontend;    npm test                  # formatting / highlighting unit tests
python scripts/e2e_smoke_test.py          # 55 end-to-end checks against the running stack (admin checks use ADMIN_EMAIL/ADMIN_PASSWORD from .env)
```

The integration tests cover: unauthenticated rejection, USER blocked from admin APIs, ADMIN access, role changes (authorised, audited,
old sessions revoked), deactivated users locked out, last-administrator protection, dataset cascade deletion, maintenance mode,
settings validation, read-only audit logs and absence of secrets in responses.

## Public datasets

Use publicly available datasets — ReviewLens does not scrape Amazon. *Consumer Reviews of Amazon Products* (Datafiniti, Kaggle) uploads
directly; *Amazon Reviews 2023* (McAuley Lab) JSONL files can be converted with `sample-data/jsonl_to_csv.py`.
