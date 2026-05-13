package com.nistula.messaging.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nistula.messaging.api.dto.WebhookRequestDto;
import com.nistula.messaging.api.dto.WebhookResponseDto;
import com.nistula.messaging.config.CompletionProperties;
import com.nistula.messaging.domain.ClassificationResult;
import com.nistula.messaging.domain.UnifiedInboundMessage;
import com.nistula.messaging.service.CompletionFailureException;
import com.nistula.messaging.service.CompletionService;
import com.nistula.messaging.service.ConfidenceService;
import com.nistula.messaging.service.GuestMessageClassifier;
import com.nistula.messaging.util.TextWords;
import com.nistula.messaging.util.TimestampValidation;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final CompletionProperties completionProperties;
    private final CompletionService completionService;
    private final ObjectMapper objectMapper;

    public WebhookController(
            CompletionProperties completionProperties, CompletionService completionService, ObjectMapper objectMapper) {
        this.completionProperties = completionProperties;
        this.completionService = completionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/webhook/message")
    public WebhookResponseDto webhookMessage(@Valid @RequestBody WebhookRequestDto payload) {
        if (!TimestampValidation.isValidIso8601(payload.getTimestamp())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "timestamp needs to be ISO8601, e.g. 2026-05-05T10:30:00Z");
        }

        var missing = new java.util.ArrayList<String>();
        if (isBlank(completionProperties.getApiUrl())) {
            missing.add("COMPLETION_API_URL");
        }
        if (isBlank(completionProperties.getApiKey())) {
            missing.add("COMPLETION_API_KEY");
        }
        if (isBlank(completionProperties.getModelId())) {
            missing.add("COMPLETION_MODEL_ID");
        }
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "missing env: " + String.join(", ", missing));
        }

        Map<String, String> extraHeaders = parseExtraHeaders(completionProperties.getHeadersJson());

        ClassificationResult classification = GuestMessageClassifier.classify(payload.getMessage());
        UUID messageId = UUID.randomUUID();

        String bookingRef = blankToNull(trimOrNull(payload.getBookingRef()));
        String propertyId = blankToNull(trimOrNull(payload.getPropertyId()));

        UnifiedInboundMessage unified = new UnifiedInboundMessage(
                messageId,
                payload.getSource(),
                payload.getGuestName().strip(),
                payload.getMessage().strip(),
                payload.getTimestamp(),
                bookingRef,
                propertyId,
                classification.queryType());

        String drafted;
        try {
            drafted = completionService.draftReply(
                    unified,
                    completionProperties.getApiUrl().strip(),
                    completionProperties.getApiKey().strip(),
                    completionProperties.getModelId().strip(),
                    extraHeaders);
        } catch (CompletionFailureException e) {
            log.error("completion call failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }

        int replyWords = TextWords.countWords(drafted);
        double confidence = ConfidenceService.compute(unified, classification, replyWords);
        var action = ConfidenceService.actionFor(confidence, unified.queryType());

        WebhookResponseDto out = new WebhookResponseDto();
        out.setMessageId(messageId);
        out.setQueryType(unified.queryType());
        out.setDraftedReply(drafted);
        out.setConfidenceScore(confidence);
        out.setAction(action);
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private Map<String, String> parseExtraHeaders(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(t);
            if (!node.isObject()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "COMPLETION_HEADERS_JSON must be a json object");
            }
            Map<String, String> out = new HashMap<>();
            node.fields().forEachRemaining(e -> {
                if (e.getValue().isTextual()) {
                    out.put(e.getKey(), e.getValue().asText());
                }
            });
            return out;
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "COMPLETION_HEADERS_JSON invalid json: " + e.getMessage());
        }
    }
}
