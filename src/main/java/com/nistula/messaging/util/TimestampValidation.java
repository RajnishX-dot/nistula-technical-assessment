package com.nistula.messaging.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public final class TimestampValidation {

    private TimestampValidation() {}

    public static boolean isValidIso8601(String v) {
        if (v == null || v.isBlank()) {
            return false;
        }
        try {
            if (v.endsWith("Z")) {
                OffsetDateTime.parse(v.replace("Z", "+00:00"));
            } else {
                OffsetDateTime.parse(v);
            }
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
