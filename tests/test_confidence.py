from uuid import uuid4

from app.classifier import ClassificationResult
from app.confidence import action_for, compute_confidence
from app.schemas import MessageSource, QueryType, UnifiedInboundMessage


def _u(q: QueryType, text: str, booking: bool = True) -> UnifiedInboundMessage:
    return UnifiedInboundMessage(
        message_id=uuid4(),
        source=MessageSource.WHATSAPP,
        guest_name="Test",
        message_text=text,
        timestamp="2026-05-05T10:30:00Z",
        booking_ref="NIS-1" if booking else None,
        property_id="villa-b1" if booking else None,
        query_type=q,
    )


def test_complaint_escalates() -> None:
    u = _u(QueryType.COMPLAINT, "refund now unacceptable")
    c = ClassificationResult(QueryType.COMPLAINT, 0.95)
    conf = compute_confidence(u, c, reply_word_count=40)
    assert action_for(conf, u.query_type) == "escalate"


def test_auto_send_band() -> None:
    u = _u(QueryType.PRE_SALES_AVAILABILITY, "Is it free on April 2 for two nights?")
    c = ClassificationResult(QueryType.PRE_SALES_AVAILABILITY, 0.92)
    conf = compute_confidence(u, c, reply_word_count=60)
    assert conf > 0.85
    assert action_for(conf, u.query_type) == "auto_send"


def test_agent_review_band() -> None:
    u = _u(
        QueryType.GENERAL_ENQUIRY,
        "Do you allow small pets for a weekend stay? We have one trained dog.",
    )
    c = ClassificationResult(QueryType.GENERAL_ENQUIRY, 0.62)
    conf = compute_confidence(u, c, reply_word_count=50)
    assert 0.60 <= conf <= 0.85
    assert action_for(conf, u.query_type) == "agent_review"
