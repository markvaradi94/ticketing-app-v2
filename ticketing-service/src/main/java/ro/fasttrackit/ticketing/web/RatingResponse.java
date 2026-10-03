package ro.fasttrackit.ticketing.web;

import ro.fasttrackit.ticketing.domain.RatingSummary;

public record RatingResponse(String eventId, Double averageRating, long reviewCount) {

    public static RatingResponse from(RatingSummary summary) {
        return new RatingResponse(summary.eventId(), summary.averageRating(), summary.reviewCount());
    }
}
