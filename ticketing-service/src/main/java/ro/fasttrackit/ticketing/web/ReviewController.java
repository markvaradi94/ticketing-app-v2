package ro.fasttrackit.ticketing.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.fasttrackit.ticketing.service.ReviewService;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/events/{eventId}")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/reviews")
    public ResponseEntity<?> addReview(@PathVariable String eventId, @Valid @RequestBody ReviewRequestBody body) {
        return reviewService.addReview(eventId, body.author(), body.rating(), body.comment())
                .<ResponseEntity<?>>map(review -> ResponseEntity.status(CREATED).body(ReviewResponse.from(review)))
                .orElseGet(() -> notFound(eventId));
    }

    @GetMapping("/reviews")
    public List<ReviewResponse> reviews(@PathVariable String eventId) {
        return reviewService.reviewsFor(eventId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @GetMapping("/rating")
    public ResponseEntity<?> rating(@PathVariable String eventId) {
        return reviewService.ratingFor(eventId)
                .<ResponseEntity<?>>map(summary -> ResponseEntity.ok(RatingResponse.from(summary)))
                .orElseGet(() -> notFound(eventId));
    }

    private static ResponseEntity<?> notFound(String eventId) {
        return ResponseEntity.of(ProblemDetail.forStatusAndDetail(NOT_FOUND, "No event with id " + eventId)).build();
    }
}
