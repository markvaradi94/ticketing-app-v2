package ro.fasttrackit.ticketing.domain;

/**
 * The reviews of one event, summed up. {@code averageRating} is {@code null} when the event has no reviews.
 */
public record RatingSummary(String eventId, Double averageRating, long reviewCount) {
}
