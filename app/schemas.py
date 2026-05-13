from datetime import datetime
from enum import Enum
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field, field_validator


class MessageSource(str, Enum):
    WHATSAPP = "whatsapp"
    BOOKING_COM = "booking_com"
    AIRBNB = "airbnb"
    INSTAGRAM = "instagram"
    DIRECT = "direct"


class QueryType(str, Enum):
    PRE_SALES_AVAILABILITY = "pre_sales_availability"
    PRE_SALES_PRICING = "pre_sales_pricing"
    POST_SALES_CHECKIN = "post_sales_checkin"
    SPECIAL_REQUEST = "special_request"
    COMPLAINT = "complaint"
    GENERAL_ENQUIRY = "general_enquiry"


class WebhookMessagePayload(BaseModel):
    source: MessageSource
    guest_name: str = Field(..., min_length=1, max_length=200)
    message: str = Field(..., min_length=1, max_length=8000)
    timestamp: str
    booking_ref: str | None = None
    property_id: str | None = None

    @field_validator("timestamp")
    @classmethod
    def timestamp_iso8601(cls, v: str) -> str:
        try:
            if v.endswith("Z"):
                datetime.fromisoformat(v.replace("Z", "+00:00"))
            else:
                datetime.fromisoformat(v)
        except ValueError as e:
            raise ValueError("timestamp needs to be ISO8601, e.g. 2026-05-05T10:30:00Z") from e
        return v


class UnifiedInboundMessage(BaseModel):
    message_id: UUID
    source: MessageSource
    guest_name: str
    message_text: str
    timestamp: str
    booking_ref: str | None
    property_id: str | None
    query_type: QueryType


class WebhookResponse(BaseModel):
    message_id: UUID
    query_type: QueryType
    drafted_reply: str
    confidence_score: float = Field(..., ge=0.0, le=1.0)
    action: Literal["auto_send", "agent_review", "escalate"]


class ErrorBody(BaseModel):
    error: str
    detail: str | None = None
