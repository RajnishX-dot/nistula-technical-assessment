import re
from dataclasses import dataclass

from app.schemas import QueryType


@dataclass(frozen=True)
class ClassificationResult:
    query_type: QueryType
    score: float


def _lower(text: str) -> str:
    return text.lower()


def classify_guest_message(message: str) -> ClassificationResult:
    t = _lower(message)

    def has_any(words: tuple[str, ...]) -> int:
        return sum(1 for w in words if w in t)

    complaint_hits = has_any(
        (
            "refund",
            "unacceptable",
            "not happy",
            "disappointed",
            "terrible",
            "horrible",
            "broken",
            "not working",
            "doesn't work",
            "does not work",
            "complaint",
            "angry",
            "worst",
        )
    )
    if complaint_hits >= 1 or re.search(r"\b(no|without)\s+(hot\s+)?water\b", t):
        strength = min(1.0, 0.55 + 0.15 * complaint_hits)
        return ClassificationResult(QueryType.COMPLAINT, strength)

    special_hits = has_any(
        (
            "early check",
            "late check",
            "airport",
            "transfer",
            "pickup",
            "pick up",
            "chef",
            "extra bed",
            "decoration",
            "celebration",
        )
    )
    if special_hits >= 1:
        return ClassificationResult(QueryType.SPECIAL_REQUEST, min(1.0, 0.5 + 0.2 * special_hits))

    post_hits = has_any(
        (
            "wifi",
            "wi-fi",
            "password",
            "check-in time",
            "check in time",
            "checkout time",
            "check-out time",
            "check out time",
            "check-in",
            "check in",
            "arrival",
            "keys",
            "address",
            "directions",
        )
    )
    if post_hits >= 1 and not re.search(r"\b(rate|price|cost|available|availability)\b", t):
        return ClassificationResult(QueryType.POST_SALES_CHECKIN, min(1.0, 0.45 + 0.15 * post_hits))

    pricing_hits = has_any(
        (
            "rate",
            "price",
            "pricing",
            "cost",
            "how much",
            "per night",
            "total for",
            "invoice",
        )
    )
    availability_hits = has_any(
        (
            "available",
            "availability",
            "vacant",
            "free on",
            "open on",
            "booked",
        )
    )
    date_like = bool(re.search(r"\b\d{1,2}\s*(am|pm)?\b", t)) or bool(
        re.search(
            r"\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\b",
            t,
        )
    )

    if availability_hits >= 1 and pricing_hits >= 1:
        return ClassificationResult(
            QueryType.PRE_SALES_AVAILABILITY,
            min(1.0, 0.55 + 0.1 * (availability_hits + pricing_hits)),
        )

    if pricing_hits >= 1 and (availability_hits == 0 or "how much" in t or "rate" in t):
        return ClassificationResult(QueryType.PRE_SALES_PRICING, min(1.0, 0.5 + 0.2 * pricing_hits))

    if availability_hits >= 1 or (date_like and ("stay" in t or "night" in t or "villa" in t)):
        return ClassificationResult(
            QueryType.PRE_SALES_AVAILABILITY,
            min(1.0, 0.45 + 0.15 * max(availability_hits, 1 if date_like else 0)),
        )

    if pricing_hits >= 1:
        return ClassificationResult(QueryType.PRE_SALES_PRICING, min(1.0, 0.5 + 0.2 * pricing_hits))

    general_hits = has_any(
        (
            "pet",
            "pets",
            "parking",
            "smoking",
            "children",
            "kid",
            "infant",
            "crib",
            "pool",
            "breakfast",
            "included",
            "amenities",
        )
    )
    if general_hits >= 1:
        return ClassificationResult(QueryType.GENERAL_ENQUIRY, min(1.0, 0.45 + 0.12 * general_hits))

    return ClassificationResult(QueryType.GENERAL_ENQUIRY, 0.35)
