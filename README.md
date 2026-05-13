# Nistula guest messaging

FastAPI webhook: validate inbound payload, normalise, call a configurable HTTP completion endpoint for a suggested reply, then return confidence and routing action.

## Environment

Copy `.env.example` to `.env` and set:

| Variable | Purpose |
|----------|---------|
| `COMPLETION_API_URL` | POST URL for the provider’s text completion endpoint |
| `COMPLETION_API_KEY` | API key (sent as `x-api-key`) |
| `COMPLETION_MODEL_ID` | Model / deployment identifier required by that endpoint |
| `COMPLETION_HEADERS_JSON` | Optional. JSON object of extra headers (string keys and string values only). Omit if the provider needs no extra headers. |

Do not commit `.env`.

The client in `app/completion.py` builds the POST JSON, merges optional headers, reads text blocks from the HTTP JSON response, then extracts `drafted_reply` from that text. Change parsing if your provider differs.

## Setup

Python 3.11+. Example for Windows:

```bash
cd nistula-technical-assessment
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8000
```

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

Response: `message_id`, `query_type`, `drafted_reply`, `confidence_score`, `action` (`auto_send` | `agent_review` | `escalate`).

`GET /health` returns `{"status":"ok"}`.

## Behaviour

Pydantic validation. `query_type` from `app/classifier.py`. Unified row with UUID and `message` → `message_text`. Suggested reply from `app/completion.py` (HTTP POST + response parsing in source). Confidence in `app/confidence.py`. `query_type` `complaint` always yields `escalate`.

## Confidence

Heuristic for routing risk: classifier score, small boost when `booking_ref` / `property_id` present, penalties for very short/long guest text and very short/long replies, lower cap for `complaint`. Thresholds: `<0.60` → `escalate`, `>0.85` → `auto_send` (unless complaint), else `agent_review`.

## Errors

422 validation (FastAPI). 503 if required env vars missing or bad `COMPLETION_HEADERS_JSON`. 502 if the completion step fails. 500 otherwise; see logs.

## Tests

```bash
pytest -q
```

## Example requests

```bash
curl -s http://localhost:8000/webhook/message -H "content-type: application/json" -d "{\"source\":\"whatsapp\",\"guest_name\":\"Rahul Sharma\",\"message\":\"Is the villa available from April 20 to 24? What is the rate for 2 adults?\",\"timestamp\":\"2026-05-05T10:30:00Z\",\"booking_ref\":\"NIS-2024-0891\",\"property_id\":\"villa-b1\"}"

curl -s http://localhost:8000/webhook/message -H "content-type: application/json" -d "{\"source\":\"airbnb\",\"guest_name\":\"Alex\",\"message\":\"WiFi password and exact check-in time please\",\"timestamp\":\"2026-05-05T11:00:00Z\",\"booking_ref\":null,\"property_id\":\"villa-b1\"}"

curl -s http://localhost:8000/webhook/message -H "content-type: application/json" -d "{\"source\":\"booking_com\",\"guest_name\":\"Sam\",\"message\":\"The AC is not working. I am not happy and I want a refund.\",\"timestamp\":\"2026-05-05T12:00:00Z\",\"booking_ref\":\"NIS-2024-0001\",\"property_id\":\"villa-b1\"}"
```

Third example should return `complaint` / `escalate`.

## Database

PostgreSQL DDL: `schema.sql`.

## Scenario write-up

`thinking.md`.

Repository: https://github.com/RajnishX-dot/nistula-technical-assessment
