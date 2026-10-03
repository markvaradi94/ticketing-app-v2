package ro.fasttrackit.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import ro.fasttrackit.ticketing.domain.RatingSummary;
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
    private final MongoTemplate mongoTemplate;

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

    public Optional<RatingSummary> ratingFor(String eventId) {
        if (!events.existsById(eventId)) {
            return Optional.empty();
        }
        Aggregation pipeline = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("eventId").is(eventId)),
                Aggregation.group("eventId").avg("rating").as("averageRating").count().as("reviewCount"));
        RatingSummary summary = Optional.ofNullable(
                        mongoTemplate.aggregate(pipeline, ReviewDocument.class, RatingGroup.class).getUniqueMappedResult())
                .map(group -> new RatingSummary(eventId, group.averageRating(), group.reviewCount()))
                .orElseGet(() -> new RatingSummary(eventId, null, 0));
        return Optional.of(summary);
    }

    // One $group result: the event id (MongoDB's _id), the average rating and the number of reviews.
    private record RatingGroup(String id, Double averageRating, long reviewCount) {
    }
}
