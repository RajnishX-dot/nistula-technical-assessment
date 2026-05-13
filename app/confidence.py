from typing import Literal

from app.classifier import ClassificationResult
from app.schemas import QueryType, UnifiedInboundMessage


def compute_confidence(
    unified: UnifiedInboundMessage,
    classification: ClassificationResult,
    reply_word_count: int,
) -> float:
    # hand-rolled score for routing, not "true" probability
    base = classification.score

    context_boost = 0.0
    if unified.booking_ref:
        context_boost += 0.04
    if unified.property_id:
        context_boost += 0.04

    text_len = len(unified.message_text)
    length_penalty = 0.0
    if text_len < 25:
        length_penalty += 0.08
    if text_len > 2000:
        length_penalty += 0.05

    reply_penalty = 0.0
    if reply_word_count < 12:
        reply_penalty += 0.1
    if reply_word_count > 220:
        reply_penalty += 0.03

    complaint_cap = 0.72 if unified.query_type == QueryType.COMPLAINT else 1.0

    raw = base + context_boost - length_penalty - reply_penalty
    raw = max(0.05, min(0.97, raw))
    raw = min(raw, complaint_cap)
    return round(raw, 2)


def action_for(confidence: float, query_type: QueryType) -> Literal["auto_send", "agent_review", "escalate"]:
    if query_type == QueryType.COMPLAINT or confidence < 0.60:
        return "escalate"
    if confidence > 0.85:
        return "auto_send"
    return "agent_review"
