package ro.fasttrackit.ticketing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Review;
import ro.fasttrackit.ticketing.domain.Venue;
import ro.fasttrackit.ticketing.persistence.EventDocument;
import ro.fasttrackit.ticketing.persistence.EventRepository;
import ro.fasttrackit.ticketing.persistence.ReviewDocument;
import ro.fasttrackit.ticketing.persistence.ReviewRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence test (real MongoDB in a Testcontainer, {@code @DataMongoTest} plus the service): checks that
 * {@link ReviewService} stores reviews for known events only and lists them newest first.
 */
@DataMongoTest
@Import(ReviewService.class)
@Testcontainers
class ReviewServiceTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    // MongoDB stores dates with millisecond precision, so the fixtures use the same.
    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @BeforeEach
    void emptyCollections() {
        reviewRepository.deleteAll();
        eventRepository.deleteAll();
        eventRepository.save(EventDocument.from(event("e1")));
        eventRepository.save(EventDocument.from(event("e2")));
    }

    private static Event event(String id) {
        return Event.builder()
                .id(id)
                .name("Event " + id)
                .venue(new Venue("Arena", "Cluj"))
                .startsAt(NOW.plusDays(7))
                .capacity(100)
                .bookedSeats(0)
                .build();
    }

    private void givenReview(String id, String eventId, LocalDateTime createdAt) {
        reviewRepository.save(ReviewDocument.from(Review.builder()
                .id(id)
                .eventId(eventId)
                .author("ana")
                .rating(4)
                .comment("nice")
                .createdAt(createdAt)
                .build()));
    }

    @Test
    void reviewOfAKnownEventIsStored() {
        Review review = reviewService.addReview("e1", "ana", 5, "great").orElseThrow();

        assertNotNull(review.getId());
        assertFalse(review.getId().isBlank());
        assertEquals("e1", review.getEventId());
        assertEquals("ana", review.getAuthor());
        assertEquals(5, review.getRating());
        assertEquals("great", review.getComment());
        assertNotNull(review.getCreatedAt());
        assertEquals(review.getCreatedAt().truncatedTo(ChronoUnit.MILLIS), review.getCreatedAt());

        Review stored = reviewRepository.findById(review.getId()).orElseThrow().toDomain();
        assertEquals(review.getEventId(), stored.getEventId());
        assertEquals(review.getAuthor(), stored.getAuthor());
        assertEquals(review.getRating(), stored.getRating());
        assertEquals(review.getComment(), stored.getComment());
        assertEquals(review.getCreatedAt(), stored.getCreatedAt());
    }

    @Test
    void reviewWithoutACommentIsStored() {
        Review review = reviewService.addReview("e1", "ana", 3, null).orElseThrow();

        List<Review> stored = reviewService.reviewsFor("e1");
        assertEquals(List.of(review), stored);
        assertNull(stored.getFirst().getComment());
    }

    @Test
    void reviewOfAnUnknownEventIsNotStored() {
        Optional<Review> review = reviewService.addReview("e9", "ana", 5, "great");

        assertTrue(review.isEmpty());
        assertEquals(0, reviewRepository.count());
    }

    @Test
    void reviewsForAnEventAreListedNewestFirst() {
        givenReview("r1", "e1", NOW.minusDays(2));
        givenReview("r2", "e2", NOW.minusDays(1));
        givenReview("r3", "e1", NOW.minusHours(1));

        List<String> ids = reviewService.reviewsFor("e1").stream().map(Review::getId).toList();

        assertEquals(List.of("r3", "r1"), ids);
    }

    @Test
    void eventWithoutReviewsHasAnEmptyList() {
        assertTrue(reviewService.reviewsFor("e1").isEmpty());
    }
}
