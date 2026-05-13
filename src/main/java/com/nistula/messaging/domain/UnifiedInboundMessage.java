package com.nistula.messaging.domain;

import java.util.UUID;

public record UnifiedInboundMessage(
        UUID messageId,
        MessageSource source,
        String guestName,
        String messageText,
        String timestamp,
        String bookingRef,
        String propertyId,
        QueryType queryType) {}
