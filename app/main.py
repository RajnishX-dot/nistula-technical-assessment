import json
import logging
from uuid import uuid4

from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import JSONResponse

from app.classifier import classify_guest_message
from app.completion import draft_reply
from app.confidence import action_for, compute_confidence
from app.config import Settings, get_settings
from app.schemas import ErrorBody, UnifiedInboundMessage, WebhookMessagePayload, WebhookResponse

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Nistula guest messaging", version="1.0.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def _extra_headers_from_settings(raw: str) -> dict[str, str]:
    raw = (raw or "").strip()
    if not raw:
        return {}
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        raise HTTPException(
            status_code=503,
            detail=f"COMPLETION_HEADERS_JSON invalid json: {e}",
        ) from e
    if not isinstance(data, dict):
        raise HTTPException(status_code=503, detail="COMPLETION_HEADERS_JSON must be a json object")
    out: dict[str, str] = {}
    for k, v in data.items():
        if isinstance(k, str) and isinstance(v, str):
            out[k] = v
    return out


@app.post(
    "/webhook/message",
    response_model=WebhookResponse,
    responses={
        400: {"model": ErrorBody},
        502: {"model": ErrorBody},
        503: {"model": ErrorBody},
    },
)
def webhook_message(
    payload: WebhookMessagePayload,
    settings: Settings = Depends(get_settings),
) -> WebhookResponse:
    missing = [
        name
        for name, val in (
            ("COMPLETION_API_URL", settings.completion_api_url),
            ("COMPLETION_API_KEY", settings.completion_api_key),
            ("COMPLETION_MODEL_ID", settings.completion_model_id),
        )
        if not (val or "").strip()
    ]
    if missing:
        raise HTTPException(
            status_code=503,
            detail=f"missing env: {', '.join(missing)}",
        )

    extra_headers = _extra_headers_from_settings(settings.completion_headers_json)

    classification = classify_guest_message(payload.message)
    message_id = uuid4()
    unified = UnifiedInboundMessage(
        message_id=message_id,
        source=payload.source,
        guest_name=payload.guest_name.strip(),
        message_text=payload.message.strip(),
        timestamp=payload.timestamp,
        booking_ref=payload.booking_ref.strip() if payload.booking_ref else None,
        property_id=payload.property_id.strip() if payload.property_id else None,
        query_type=classification.query_type,
    )

    try:
        drafted = draft_reply(
            unified,
            api_url=settings.completion_api_url.strip(),
            api_key=settings.completion_api_key.strip(),
            model=settings.completion_model_id.strip(),
            extra_headers=extra_headers,
        )
    except RuntimeError as e:
        logger.exception("draft_reply failed")
        raise HTTPException(status_code=502, detail=str(e)) from e

    reply_words = len(drafted.split())
    confidence = compute_confidence(unified, classification, reply_word_count=reply_words)
    action = action_for(confidence, unified.query_type)

    return WebhookResponse(
        message_id=message_id,
        query_type=unified.query_type,
        drafted_reply=drafted,
        confidence_score=confidence,
        action=action,
    )


@app.exception_handler(Exception)
def unhandled_exception_handler(request, exc: Exception):
    logger.exception("unhandled %s", request.url.path)
    return JSONResponse(
        status_code=500,
        content=ErrorBody(
            error="internal_server_error",
            detail="internal error",
        ).model_dump(),
    )
