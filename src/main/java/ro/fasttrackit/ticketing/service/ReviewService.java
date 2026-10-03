package ro.fasttrackit.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.fasttrackit.ticketing.domain.Review;
import ro.fasttrackit.ticketing.persistence.EventRepository;
import ro.fasttrackit.ticketing.persistence.ReviewDocument;
import ro.fasttrackit.ticketing.persistence.ReviewRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final EventRepository events;
    private final ReviewRepository reviews;

    public Optional<Review> addReview(String eventId, String author, int rating, String comment) {
        if (!events.existsById(eventId)) {
            return Optional.empty();
        }
        Review review = Review.builder()
                .id(UUID.randomUUID().toString())
                .eventId(eventId)
                .author(author)
                .rating(rating)
                .comment(comment)
                .createdAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS))
                .build();
        reviews.save(ReviewDocument.from(review));
        return Optional.of(review);
    }

    public List<Review> reviewsFor(String eventId) {
        return reviews.findByEventIdOrderByCreatedAtDesc(eventId).stream()
                .map(ReviewDocument::toDomain)
                .toList();
    }
}
