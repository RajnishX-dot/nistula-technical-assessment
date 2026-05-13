# Nistula — guest webhook (Java)

Spring Boot 3.2, Java 17. Endpoints: `POST /webhook/message`, `GET /health` (port 8080 unless you override `server.port`).

There is also `schema.sql` (Postgres) and `thinking.md`.

## Env vars

Spring picks these up from the process environment (export in your shell, or set in your IDE run config). Don’t commit real values.

- `COMPLETION_API_URL` — POST URL  
- `COMPLETION_API_KEY` — sent as `x-api-key`  
- `COMPLETION_MODEL_ID` — whatever id your provider wants in the JSON body  
- `COMPLETION_HEADERS_JSON` — optional, flat JSON object of extra string headers only  

`.env.example` is just a reminder list; this app does not auto-load a `.env` file.

## Run

```bash
cd nistula-technical-assessment
mvn test
mvn spring-boot:run
```

## Request / response

Example POST body:

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

JSON back: `message_id`, `query_type`, `drafted_reply`, `confidence_score`, `action`.

Classifier is rule-based (`GuestMessageClassifier`). Reply text comes from an HTTP POST in `CompletionService` (see that class for payload/parse details — swap it if your vendor’s JSON differs). Confidence + `action` in `ConfidenceService`; complaints always go to `escalate`.

## Curl

```bash
curl -s http://localhost:8080/webhook/message -H "content-type: application/json" -d "{\"source\":\"whatsapp\",\"guest_name\":\"Rahul Sharma\",\"message\":\"Is the villa available from April 20 to 24? What is the rate for 2 adults?\",\"timestamp\":\"2026-05-05T10:30:00Z\",\"booking_ref\":\"NIS-2024-0891\",\"property_id\":\"villa-b1\"}"
curl -s http://localhost:8080/health
```

Public repo: https://github.com/RajnishX-dot/nistula-technical-assessment
