package com.nistula.messaging.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nistula.messaging.domain.QueryType;
import com.nistula.messaging.domain.RoutingAction;
import java.util.UUID;

public class WebhookResponseDto {

    @JsonProperty("message_id")
    private UUID messageId;

    @JsonProperty("query_type")
    private QueryType queryType;

    @JsonProperty("drafted_reply")
    private String draftedReply;

    @JsonProperty("confidence_score")
    private double confidenceScore;

    private RoutingAction action;

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public void setQueryType(QueryType queryType) {
        this.queryType = queryType;
    }

    public String getDraftedReply() {
        return draftedReply;
    }

    public void setDraftedReply(String draftedReply) {
        this.draftedReply = draftedReply;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public RoutingAction getAction() {
        return action;
    }

    public void setAction(RoutingAction action) {
        this.action = action;
    }
}
