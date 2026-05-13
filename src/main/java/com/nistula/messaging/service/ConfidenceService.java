package com.nistula.messaging.service;

import com.nistula.messaging.domain.ClassificationResult;
import com.nistula.messaging.domain.QueryType;
import com.nistula.messaging.domain.RoutingAction;
import com.nistula.messaging.domain.UnifiedInboundMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ConfidenceService {

    private ConfidenceService() {}

    public static double compute(UnifiedInboundMessage unified, ClassificationResult classification, int replyWordCount) {
        double base = classification.score();

        double contextBoost = 0;
        if (unified.bookingRef() != null && !unified.bookingRef().isBlank()) {
            contextBoost += 0.04;
        }
        if (unified.propertyId() != null && !unified.propertyId().isBlank()) {
            contextBoost += 0.04;
        }

        int textLen = unified.messageText().length();
        double lengthPenalty = 0;
        if (textLen < 25) {
            lengthPenalty += 0.08;
        }
        if (textLen > 2000) {
            lengthPenalty += 0.05;
        }

        double replyPenalty = 0;
        if (replyWordCount < 12) {
            replyPenalty += 0.1;
        }
        if (replyWordCount > 220) {
            replyPenalty += 0.03;
        }

        double complaintCap = unified.queryType() == QueryType.complaint ? 0.72 : 1.0;

        double raw = base + contextBoost - lengthPenalty - replyPenalty;
        raw = Math.max(0.05, Math.min(0.97, raw));
        raw = Math.min(raw, complaintCap);
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static RoutingAction actionFor(double confidence, QueryType queryType) {
        if (queryType == QueryType.complaint || confidence < 0.60) {
            return RoutingAction.escalate;
        }
        if (confidence > 0.85) {
            return RoutingAction.auto_send;
        }
        return RoutingAction.agent_review;
    }
}
