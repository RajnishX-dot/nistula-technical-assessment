# nistula take-home

FastAPI webhook for Part 1, `schema.sql` for Part 2, `thinking.md` for Part 3.

Copy `.env.example` to `.env` and put `ANTHROPIC_API_KEY` there. Don't commit `.env`.

## run it

Python 3.11+. Windows paths below; on mac/linux swap the venv line.

```bash
cd nistula-technical-assessment
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8000
```

## POST /webhook/message

Body matches the brief, e.g.

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

Response: `message_id`, `query_type`, `drafted_reply`, `confidence_score`, `action` (`auto_send` / `agent_review` / `escalate`).

`GET /health` -> `{"status":"ok"}`.

## what the code actually does

Validates input, slaps a UUID on it, renames `message` -> `message_text`, runs a dumb keyword classifier for `query_type` (cheap, same every time), calls Claude with the villa facts hardcoded in `app/claude_service.py`, parses `{"drafted_reply": "..."}` from the model, then scores confidence and picks an action.

Complaints always escalate even if the score math looks high. That's intentional.

## confidence (rough)

Not "model certainty". More "would I let this fire without a second pair of eyes".

Classifier strength + small bump if `booking_ref` / `property_id` exist, small penalties for very short guest text / very long guest text / very short or very long replies. Complaints get capped lower.

Thresholds: `<0.60` escalate, `>0.85` auto_send (unless complaint), else agent_review.

## errors

422 validation noise is whatever FastAPI returns. 503 if the key is missing. 502 if Claude blows up or we can't parse JSON. 500 otherwise (details in logs).

## tests

```bash
pytest -q
```

No network for those.

## curls I used while wiring it

```bash
curl -s http://localhost:8000/webhook/message -H "content-type: application/json" -d "{\"source\":\"whatsapp\",\"guest_name\":\"Rahul Sharma\",\"message\":\"Is the villa available from April 20 to 24? What is the rate for 2 adults?\",\"timestamp\":\"2026-05-05T10:30:00Z\",\"booking_ref\":\"NIS-2024-0891\",\"property_id\":\"villa-b1\"}"

curl -s http://localhost:8000/webhook/message -H "content-type: application/json" -d "{\"source\":\"airbnb\",\"guest_name\":\"Alex\",\"message\":\"WiFi password and exact check-in time please\",\"timestamp\":\"2026-05-05T11:00:00Z\",\"booking_ref\":null,\"property_id\":\"villa-b1\"}"

curl -s http://localhost:8000/webhook/message -H "content-type: application/json" -d "{\"source\":\"booking_com\",\"guest_name\":\"Sam\",\"message\":\"The AC is not working. I am not happy and I want a refund.\",\"timestamp\":\"2026-05-05T12:00:00Z\",\"booking_ref\":\"NIS-2024-0001\",\"property_id\":\"villa-b1\"}"
```

Third one should land as `complaint` / `escalate`.

## part 2

All in `schema.sql`. I stuck inbound classifier fields and outbound "who sent what" metadata on the same `messages` table with CHECKs so support gets one timeline without weird NULL combos. Could have split outbound lineage out; didn't want another join every time someone scrolls a thread.

## part 3

`thinking.md` (word limit in there).

## ship it (github)

Assessment asked for a public repo named `nistula-technical-assessment`.

```bash
cd nistula-technical-assessment
git init
git add -A
git commit -m "nistula assessment: webhook, schema, writeup"
git branch -M main
git remote add origin https://github.com/RajnishX-dot/nistula-technical-assessment.git
git push -u origin main
```

Create an **empty** public repo `nistula-technical-assessment` under [RajnishX-dot](https://github.com/RajnishX-dot) first (no README/license if GitHub offers that), then run the block above. Submission link will be `https://github.com/RajnishX-dot/nistula-technical-assessment` once `push` finishes.
