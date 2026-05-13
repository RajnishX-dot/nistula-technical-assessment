package com.nistula.messaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nistula.messaging.domain.QueryType;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GuestMessageClassifierTest {

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(
                        "Is the villa available from April 20 to 24? What is the rate for 2 adults?",
                        QueryType.pre_sales_availability),
                Arguments.of("What is the total for 3 nights for 2 people?", QueryType.pre_sales_pricing),
                Arguments.of("WiFi password please?", QueryType.post_sales_checkin),
                Arguments.of("The AC is broken and I want a refund.", QueryType.complaint),
                Arguments.of("Can we get airport pickup at 1am?", QueryType.special_request),
                Arguments.of("Do you allow pets?", QueryType.general_enquiry));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void classifyGuestMessage(String message, QueryType expected) {
        assertEquals(expected, GuestMessageClassifier.classify(message).queryType());
    }
}
