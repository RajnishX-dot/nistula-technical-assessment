import json
import re
from typing import Any

import httpx

from app.schemas import UnifiedInboundMessage

PROPERTY_CONTEXT = """Property: Villa B1, Assagao, North Goa
Bedrooms: 3 | Max guests: 6 | Private pool: Yes
Check-in: 2pm | Check-out: 11am
Base rate: INR 18,000 per night (up to 4 guests)
Extra guest: INR 2,000 per night per person
WiFi password: Nistula@2024
Caretaker: Available 8am to 10pm
Chef on call: Yes, pre-booking required
Availability April 20-24: Available
Cancellation: Free up to 7 days before check-in"""

SYSTEM_PROMPT = (
    "Draft a short guest reply for Nistula (Goa villas). Polite SMS style. "
    "Use only facts from the property block. If missing, say the team will confirm. "
    'Return one JSON object only: {"drafted_reply":"..."}'
)


def _extract_json_object(text: str) -> dict[str, Any]:
    text = text.strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        raise ValueError("response did not contain a json object")
    return json.loads(m.group(0))


def _text_from_message_response(payload: dict[str, Any]) -> str:
    parts: list[str] = []
    for block in payload.get("content") or []:
        if isinstance(block, dict) and block.get("type") == "text":
            t = block.get("text")
            if isinstance(t, str):
                parts.append(t)
    return "".join(parts).strip()


def draft_reply(
    unified: UnifiedInboundMessage,
    *,
    api_url: str,
    api_key: str,
    model: str,
    extra_headers: dict[str, str] | None = None,
) -> str:
    user_block = f"""Property:
{PROPERTY_CONTEXT}

query_type: {unified.query_type.value}

message:
{json.dumps(unified.model_dump(mode="json"), default=str)}

Output JSON with drafted_reply only."""

    headers: dict[str, str] = {"content-type": "application/json", "x-api-key": api_key}
    if extra_headers:
        headers.update(extra_headers)

    body = {
        "model": model,
        "max_tokens": 1024,
        "system": SYSTEM_PROMPT,
        "messages": [{"role": "user", "content": user_block}],
    }

    try:
        with httpx.Client(timeout=120.0) as client:
            r = client.post(api_url, headers=headers, json=body)
    except httpx.RequestError as e:
        raise RuntimeError(f"request failed: {e}") from e

    if r.status_code >= 400:
        raise RuntimeError(f"http {r.status_code}: {r.text[:500]}")

    try:
        outer = r.json()
    except json.JSONDecodeError as e:
        raise RuntimeError(f"invalid json in http body: {e}") from e

    combined = _text_from_message_response(outer)
    if not combined:
        raise RuntimeError("empty text in http response")

    try:
        data = _extract_json_object(combined)
    except (json.JSONDecodeError, ValueError) as e:
        raise RuntimeError(f"json parse failed: {e}; head={combined[:400]!r}") from e

    reply = data.get("drafted_reply")
    if not isinstance(reply, str) or not reply.strip():
        raise RuntimeError("drafted_reply missing or empty")
    return reply.strip()
