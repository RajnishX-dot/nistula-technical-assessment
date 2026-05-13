package com.nistula.messaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nistula.messaging.domain.ClassificationResult;
import com.nistula.messaging.domain.MessageSource;
import com.nistula.messaging.domain.QueryType;
import com.nistula.messaging.domain.RoutingAction;
import com.nistula.messaging.domain.UnifiedInboundMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfidenceServiceTest {

    private UnifiedInboundMessage u(QueryType q, String text, boolean booking) {
        return new UnifiedInboundMessage(
                UUID.randomUUID(),
                MessageSource.whatsapp,
                "Test",
                text,
                "2026-05-05T10:30:00Z",
                booking ? "NIS-1" : null,
                booking ? "villa-b1" : null,
                q);
    }

    @Test
    void complaintEscalates() {
        UnifiedInboundMessage msg = u(QueryType.complaint, "refund now unacceptable", true);
        ClassificationResult c = new ClassificationResult(QueryType.complaint, 0.95);
        double conf = ConfidenceService.compute(msg, c, 40);
        assertEquals(RoutingAction.escalate, ConfidenceService.actionFor(conf, msg.queryType()));
    }

    @Test
    void autoSendBand() {
        UnifiedInboundMessage msg = u(QueryType.pre_sales_availability, "Is it free on April 2 for two nights?", true);
        ClassificationResult c = new ClassificationResult(QueryType.pre_sales_availability, 0.92);
        double conf = ConfidenceService.compute(msg, c, 60);
        assertTrue(conf > 0.85);
        assertEquals(RoutingAction.auto_send, ConfidenceService.actionFor(conf, msg.queryType()));
    }

    @Test
    void agentReviewBand() {
        UnifiedInboundMessage msg = u(
                QueryType.general_enquiry,
                "Do you allow small pets for a weekend stay? We have one trained dog.",
                true);
        ClassificationResult c = new ClassificationResult(QueryType.general_enquiry, 0.62);
        double conf = ConfidenceService.compute(msg, c, 50);
        assertTrue(conf >= 0.60 && conf <= 0.85);
        assertEquals(RoutingAction.agent_review, ConfidenceService.actionFor(conf, msg.queryType()));
    }
}
