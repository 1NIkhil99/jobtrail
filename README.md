# JobTrail

A job application tracking REST API built with Spring Boot 3.3, Java 17, and PostgreSQL.

Track applications through their lifecycle (`SAVED → APPLIED → OA → PHONE_SCREEN → INTERVIEW → OFFER / REJECTED / WITHDRAWN`), with a full status-change history per application and per-user analytics.

## Stack

- Java 17, Spring Boot 3.3, Maven
- Spring Data JPA (Hibernate) + PostgreSQL 16
- Spring Security with stateless JWT auth (jjwt)
- JUnit 5, Mockito, Testcontainers
- Docker Compose, GitHub Actions CI

## Running locally

Start Postgres and the app together:

```bash
docker compose up --build
```

Or run the app from your IDE / Maven against a local database:

```bash
docker compose up -d db
mvn spring-boot:run
```

The API is served at `http://localhost:8080`. On an empty database a demo
account is seeded: `demo@jobtrail.dev` / `password123`.

## Browsing the API

There is no web frontend — JobTrail is a JSON API. Interactive docs are served at:

```
http://localhost:8080/swagger-ui.html
```

Log in via `POST /api/v1/auth/login` with the demo credentials, copy the returned
token, click **Authorize**, and paste it to call the protected endpoints from the
browser. The raw OpenAPI document is at `/v3/api-docs`.

## API

All endpoints except `/api/v1/auth/**` require `Authorization: Bearer <token>`.

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Register, returns a JWT |
| POST | `/api/v1/auth/login` | Login, returns a JWT |
| POST | `/api/v1/applications` | Create an application |
| GET | `/api/v1/applications` | Paginated list, optional `?status=`, `?page=`, `?size=`, `?sort=` |
| GET | `/api/v1/applications/{id}` | Application with full status history |
| PUT | `/api/v1/applications/{id}` | Update; a status change appends a status event |
| DELETE | `/api/v1/applications/{id}` | Delete |
| GET | `/api/v1/analytics/summary` | Totals and per-status counts |

### Example

```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@jobtrail.dev","password":"password123"}' | jq -r .token)

curl -s localhost:8080/api/v1/applications \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"companyName":"Acme","position":"Backend Engineer","status":"APPLIED"}'

curl -s "localhost:8080/api/v1/applications?status=APPLIED&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
```

## Design notes

- Every query is scoped by the authenticated user id; a foreign application id
  resolves to `404`, so resource existence is never leaked across accounts.
- The list endpoint join-fetches only the company. Fetching the status-event
  collection alongside pagination would force Hibernate into in-memory
  pagination, so the history is returned only on the detail endpoint.
- Status events are managed through the `Application` aggregate and persisted
  by cascade, keeping the history consistent with the current status.
- Companies are deduplicated case-insensitively on create/update.

## Tests

```bash
mvn verify
```

Unit tests cover the service layer with Mockito. Integration tests boot the
full application against a disposable PostgreSQL 16 Testcontainer, covering
auth, ownership scoping, validation, status-history behavior, and analytics.
Docker must be running for the integration tests.
