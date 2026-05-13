# Nistula guest messaging

Java 17 + Spring Boot 3.2. Same HTTP API as before: `POST /webhook/message`, `GET /health`.

Also in this repo: `schema.sql` (Postgres DDL), `thinking.md` (scenario answers).

## Configuration

Set environment variables (or put equivalent keys in `src/main/resources/application.properties` for local dev only — do not commit secrets):

| Variable | Purpose |
|----------|---------|
| `COMPLETION_API_URL` | POST URL for the completion endpoint |
| `COMPLETION_API_KEY` | API key (sent as header `x-api-key`) |
| `COMPLETION_MODEL_ID` | Model / deployment id required by that endpoint |
| `COMPLETION_HEADERS_JSON` | Optional JSON object of extra string headers |

Copy `.env.example` as a checklist; Spring reads **process environment** by default (not `.env` files). Use your shell, IDE run config, or a process manager to export the variables.

## Build and run

```bash
cd nistula-technical-assessment
mvn -q test
mvn spring-boot:run
```

Default port: **8080**.

## API

`POST /webhook/message` — JSON body:

```json
{
  "source": "whatsapp",
  "guest_name": "Rahul Sharma",
  "message": "Is the villa available from April 20 to 24? What is the rate for 2 adults?",
  "timestamp": "2026-05-05T10:30:00Z",
  "booking_ref": "NIS-2024-0891",
  "property_id": "villa-b1"
}
```

Response JSON fields: `message_id`, `query_type`, `drafted_reply`, `confidence_score`, `action`.

## Behaviour

Validation on the request DTO. `query_type` from `GuestMessageClassifier`. Unified message + UUID. Draft from `CompletionService` (HTTP POST + JSON parse in source). Confidence from `ConfidenceService`. `complaint` always routes to `escalate`.

## Tests

```bash
mvn test
```

Classifier and confidence unit tests live under `src/test/java`.

## Example curls

```bash
curl -s http://localhost:8080/webhook/message -H "content-type: application/json" -d "{\"source\":\"whatsapp\",\"guest_name\":\"Rahul Sharma\",\"message\":\"Is the villa available from April 20 to 24? What is the rate for 2 adults?\",\"timestamp\":\"2026-05-05T10:30:00Z\",\"booking_ref\":\"NIS-2024-0891\",\"property_id\":\"villa-b1\"}"

curl -s http://localhost:8080/health
```

## Database

`schema.sql`

## Scenario write-up

`thinking.md`

Repository: https://github.com/RajnishX-dot/nistula-technical-assessment
