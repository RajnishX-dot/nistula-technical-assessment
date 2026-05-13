import logging
from uuid import uuid4

from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import JSONResponse

from app.classifier import classify_guest_message
from app.claude_service import draft_reply
from app.confidence import action_for, compute_confidence
from app.config import Settings, get_settings
from app.schemas import ErrorBody, UnifiedInboundMessage, WebhookMessagePayload, WebhookResponse

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="nistula guest webhook", version="1.0.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


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
    if not settings.anthropic_api_key.strip():
        raise HTTPException(
            status_code=503,
            detail="ANTHROPIC_API_KEY missing in .env",
        )

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
            api_key=settings.anthropic_api_key,
            model=settings.claude_model,
        )
    except RuntimeError as e:
        logger.exception("claude/draft_reply failed")
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
        content=ErrorBody(error="internal_server_error", detail="server blew up, check logs").model_dump(),
    )
