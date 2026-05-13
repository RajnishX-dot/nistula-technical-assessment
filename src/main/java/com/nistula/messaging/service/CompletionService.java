package com.nistula.messaging.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nistula.messaging.domain.UnifiedInboundMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CompletionService {

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");

    private static final String PROPERTY_CONTEXT =
            """
            Property: Villa B1, Assagao, North Goa
            Bedrooms: 3 | Max guests: 6 | Private pool: Yes
            Check-in: 2pm | Check-out: 11am
            Base rate: INR 18,000 per night (up to 4 guests)
            Extra guest: INR 2,000 per night per person
            WiFi password: Nistula@2024
            Caretaker: Available 8am to 10pm
            Chef on call: Yes, pre-booking required
            Availability April 20-24: Available
            Cancellation: Free up to 7 days before check-in"""
                    .strip();

    private static final String SYSTEM_PROMPT =
            "You are replying as Nistula (Goa villas). Keep it short and normal. "
                    + "Stick to the property facts in the message; if something isn’t listed, say you’ll confirm with the team. "
                    + "Reply as a single JSON object: {\"drafted_reply\":\"...\"}";

    private final ObjectMapper objectMapper;

    public CompletionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String draftReply(
            UnifiedInboundMessage unified,
            String apiUrl,
            String apiKey,
            String modelId,
            Map<String, String> extraHeaders) {
        String userBlock =
                "Property:\n"
                        + PROPERTY_CONTEXT
                        + "\n\nquery_type: "
                        + unified.queryType().name()
                        + "\n\nmessage:\n"
                        + toJson(unified)
                        + "\n\nOutput JSON with drafted_reply only.";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelId);
        body.put("max_tokens", 1024);
        body.put("system", SYSTEM_PROMPT);
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userBlock);

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new CompletionFailureException("failed to serialize request", e);
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey);
        if (extraHeaders != null) {
            extraHeaders.forEach(rb::header);
        }
        HttpRequest request = rb.POST(HttpRequest.BodyPublishers.ofString(bodyJson)).build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionFailureException("request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() >= 400) {
            String tail = response.body() == null ? "" : response.body();
            if (tail.length() > 500) {
                tail = tail.substring(0, 500);
            }
            throw new CompletionFailureException("http " + response.statusCode() + ": " + tail);
        }

        JsonNode outer;
        try {
            outer = objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new CompletionFailureException("invalid json in http body: " + e.getMessage(), e);
        }

        String combined = textFromMessageResponse(outer);
        if (combined.isBlank()) {
            throw new CompletionFailureException("empty text in http response");
        }

        JsonNode data;
        try {
            data = extractJsonObject(combined);
        } catch (IOException e) {
            throw new CompletionFailureException(
                    "json parse failed: " + e.getMessage() + "; head=" + abbreviate(combined, 400), e);
        }

        JsonNode replyNode = data.get("drafted_reply");
        if (replyNode == null || !replyNode.isTextual() || replyNode.asText().isBlank()) {
            throw new CompletionFailureException("drafted_reply missing or empty");
        }
        return replyNode.asText().strip();
    }

    private String toJson(UnifiedInboundMessage u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message_id", u.messageId().toString());
        m.put("source", u.source().name());
        m.put("guest_name", u.guestName());
        m.put("message_text", u.messageText());
        m.put("timestamp", u.timestamp());
        m.put("booking_ref", u.bookingRef());
        m.put("property_id", u.propertyId());
        m.put("query_type", u.queryType().name());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (IOException e) {
            throw new CompletionFailureException("failed to serialize unified message", e);
        }
    }

    private static String textFromMessageResponse(JsonNode payload) {
        StringBuilder sb = new StringBuilder();
        JsonNode content = payload.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode block : content) {
                if (block.path("type").asText("").equals("text")) {
                    sb.append(block.path("text").asText(""));
                }
            }
        }
        return sb.toString().trim();
    }

    private JsonNode extractJsonObject(String text) throws IOException {
        String t = text.strip();
        Matcher m = JSON_OBJECT.matcher(t);
        if (!m.find()) {
            throw new IOException("response did not contain a json object");
        }
        return objectMapper.readTree(m.group());
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "null";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
