import json
import re
from typing import Any

from anthropic import APIError, Anthropic

from app.schemas import UnifiedInboundMessage

# from the brief — don't invent beyond this
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

SYSTEM_PROMPT = """You reply as Nistula (villas in Goa). Sound like a normal person texting a guest: short, friendly, no corporate filler.
Only use facts from the property block. If you don't know, say you'll confirm with the team — don't guess.
Reply as JSON only: {"drafted_reply":"..."}  (no markdown, no extra keys)"""


def _extract_json_object(text: str) -> dict[str, Any]:
    text = text.strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        raise ValueError("no json object in model output")
    return json.loads(m.group(0))


def draft_reply(unified: UnifiedInboundMessage, api_key: str, model: str) -> str:
    client = Anthropic(api_key=api_key)
    user_block = f"""Property:
{PROPERTY_CONTEXT}

query_type (for tone): {unified.query_type.value}

payload:
{json.dumps(unified.model_dump(mode="json"), default=str)}

Write drafted_reply JSON only."""

    try:
        msg = client.messages.create(
            model=model,
            max_tokens=1024,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": user_block}],
        )
    except APIError as e:
        raise RuntimeError(f"anthropic api: {e}") from e

    if not msg.content:
        raise RuntimeError("empty message from anthropic")

    chunks: list[str] = []
    for block in msg.content:
        if hasattr(block, "text"):
            chunks.append(block.text)
    combined = "".join(chunks).strip()
    if not combined:
        raise RuntimeError("no text blocks from anthropic")

    try:
        data = _extract_json_object(combined)
    except (json.JSONDecodeError, ValueError) as e:
        raise RuntimeError(f"bad json from model: {e}; head={combined[:400]!r}") from e

    reply = data.get("drafted_reply")
    if not isinstance(reply, str) or not reply.strip():
        raise RuntimeError("drafted_reply missing/empty")
    return reply.strip()
