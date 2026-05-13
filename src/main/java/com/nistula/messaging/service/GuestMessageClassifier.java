package com.nistula.messaging.service;

import com.nistula.messaging.domain.ClassificationResult;
import com.nistula.messaging.domain.QueryType;
import java.util.regex.Pattern;

public final class GuestMessageClassifier {

    private static final Pattern NO_WATER =
            Pattern.compile("\\b(no|without)\\s+(hot\\s+)?water\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RATE_PRICE =
            Pattern.compile("\\b(rate|price|cost|available|availability)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_NUM =
            Pattern.compile("\\b\\d{1,2}\\s*(am|pm)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_WORD = Pattern.compile(
            "\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\b",
            Pattern.CASE_INSENSITIVE);

    private GuestMessageClassifier() {}

    public static ClassificationResult classify(String message) {
        String t = message.toLowerCase();

        int complaintHits = hasAny(
                t,
                "refund",
                "unacceptable",
                "not happy",
                "disappointed",
                "terrible",
                "horrible",
                "broken",
                "not working",
                "doesn't work",
                "does not work",
                "complaint",
                "angry",
                "worst");
        if (complaintHits >= 1 || NO_WATER.matcher(t).find()) {
            double strength = Math.min(1.0, 0.55 + 0.15 * complaintHits);
            return new ClassificationResult(QueryType.complaint, strength);
        }

        int specialHits = hasAny(
                t,
                "early check",
                "late check",
                "airport",
                "transfer",
                "pickup",
                "pick up",
                "chef",
                "extra bed",
                "decoration",
                "celebration");
        if (specialHits >= 1) {
            return new ClassificationResult(QueryType.special_request, Math.min(1.0, 0.5 + 0.2 * specialHits));
        }

        int postHits = hasAny(
                t,
                "wifi",
                "wi-fi",
                "password",
                "check-in time",
                "check in time",
                "checkout time",
                "check-out time",
                "check out time",
                "check-in",
                "check in",
                "arrival",
                "keys",
                "address",
                "directions");
        if (postHits >= 1 && !RATE_PRICE.matcher(t).find()) {
            return new ClassificationResult(QueryType.post_sales_checkin, Math.min(1.0, 0.45 + 0.15 * postHits));
        }

        int pricingHits = hasAny(
                t, "rate", "price", "pricing", "cost", "how much", "per night", "total for", "invoice");
        int availabilityHits =
                hasAny(t, "available", "availability", "vacant", "free on", "open on", "booked");
        boolean dateLike = DATE_NUM.matcher(t).find() || DATE_WORD.matcher(t).find();

        if (availabilityHits >= 1 && pricingHits >= 1) {
            return new ClassificationResult(
                    QueryType.pre_sales_availability,
                    Math.min(1.0, 0.55 + 0.1 * (availabilityHits + pricingHits)));
        }

        if (pricingHits >= 1 && (availabilityHits == 0 || t.contains("how much") || t.contains("rate"))) {
            return new ClassificationResult(QueryType.pre_sales_pricing, Math.min(1.0, 0.5 + 0.2 * pricingHits));
        }

        if (availabilityHits >= 1 || (dateLike && (t.contains("stay") || t.contains("night") || t.contains("villa")))) {
            return new ClassificationResult(
                    QueryType.pre_sales_availability,
                    Math.min(1.0, 0.45 + 0.15 * Math.max(availabilityHits, dateLike ? 1 : 0)));
        }

        if (pricingHits >= 1) {
            return new ClassificationResult(QueryType.pre_sales_pricing, Math.min(1.0, 0.5 + 0.2 * pricingHits));
        }

        int generalHits = hasAny(
                t,
                "pet",
                "pets",
                "parking",
                "smoking",
                "children",
                "kid",
                "infant",
                "crib",
                "pool",
                "breakfast",
                "included",
                "amenities");
        if (generalHits >= 1) {
            return new ClassificationResult(QueryType.general_enquiry, Math.min(1.0, 0.45 + 0.12 * generalHits));
        }

        return new ClassificationResult(QueryType.general_enquiry, 0.35);
    }

    private static int hasAny(String haystack, String... needles) {
        int c = 0;
        for (String n : needles) {
            if (haystack.contains(n)) {
                c++;
            }
        }
        return c;
    }
}
