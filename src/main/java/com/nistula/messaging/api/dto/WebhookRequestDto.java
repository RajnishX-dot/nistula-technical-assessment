package com.nistula.messaging.api.dto;

import com.nistula.messaging.domain.MessageSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class WebhookRequestDto {

    @NotNull
    private MessageSource source;

    @NotBlank
    @Size(min = 1, max = 200)
    private String guestName;

    @NotBlank
    @Size(min = 1, max = 8000)
    private String message;

    @NotBlank
    private String timestamp;

    @Size(max = 200)
    private String bookingRef;

    @Size(max = 200)
    private String propertyId;

    public MessageSource getSource() {
        return source;
    }

    public void setSource(MessageSource source) {
        this.source = source;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public void setBookingRef(String bookingRef) {
        this.bookingRef = bookingRef;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }
}
