import pytest

from app.classifier import classify_guest_message
from app.schemas import QueryType


@pytest.mark.parametrize(
    "message,expected",
    [
        (
            "Is the villa available from April 20 to 24? What is the rate for 2 adults?",
            QueryType.PRE_SALES_AVAILABILITY,
        ),
        ("What is the total for 3 nights for 2 people?", QueryType.PRE_SALES_PRICING),
        ("WiFi password please?", QueryType.POST_SALES_CHECKIN),
        ("The AC is broken and I want a refund.", QueryType.COMPLAINT),
        ("Can we get airport pickup at 1am?", QueryType.SPECIAL_REQUEST),
        ("Do you allow pets?", QueryType.GENERAL_ENQUIRY),
    ],
)
def test_classify_guest_message(message: str, expected: QueryType) -> None:
    assert classify_guest_message(message).query_type == expected
