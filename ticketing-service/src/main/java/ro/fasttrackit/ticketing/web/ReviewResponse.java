package ro.fasttrackit.ticketing.web;

import ro.fasttrackit.ticketing.domain.Review;

import java.time.LocalDateTime;

public record ReviewResponse(String id, String eventId, String author, int rating, String comment,
                             LocalDateTime createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getEventId(),
                review.getAuthor(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }
}
