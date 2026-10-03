package ro.fasttrackit.ticketing.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.fasttrackit.ticketing.domain.RatingSummary;
import ro.fasttrackit.ticketing.domain.Review;
import ro.fasttrackit.ticketing.service.ReviewService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller test ({@code @WebMvcTest}, no Spring context beyond MVC): checks {@link ReviewController}
 * against a mocked {@link ReviewService}.
 */
@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    private static final String VALID_BODY = """
            {"author":"ana","rating":5,"comment":"great"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    private static Review review(String id, int rating, LocalDateTime createdAt) {
        return Review.builder()
                .id(id)
                .eventId("e1")
                .author("ana")
                .rating(rating)
                .comment("great")
                .createdAt(createdAt)
                .build();
    }

    @Test
    void validReviewIsCreated() throws Exception {
        when(reviewService.addReview("e1", "ana", 5, "great"))
                .thenReturn(Optional.of(review("r1", 5, LocalDateTime.of(2030, 1, 5, 10, 30))));

        mockMvc.perform(post("/events/e1/reviews").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.eventId").value("e1"))
                .andExpect(jsonPath("$.author").value("ana"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("great"))
                .andExpect(jsonPath("$.createdAt").value("2030-01-05T10:30:00"));
    }

    @Test
    void reviewOfAnUnknownEventIsNotFoundProblemDetail() throws Exception {
        when(reviewService.addReview("e9", "ana", 5, "great")).thenReturn(Optional.empty());

        mockMvc.perform(post("/events/e9/reviews").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("e9")));
    }

    @Test
    void ratingZeroIsBadRequestProblemDetail() throws Exception {
        assertInvalidReview("""
                {"author":"ana","rating":0,"comment":"bad"}""");
    }

    @Test
    void ratingSixIsBadRequestProblemDetail() throws Exception {
        assertInvalidReview("""
                {"author":"ana","rating":6,"comment":"too good"}""");
    }

    @Test
    void blankAuthorIsBadRequestProblemDetail() throws Exception {
        assertInvalidReview("""
                {"author":" ","rating":4,"comment":"nice"}""");
    }

    @Test
    void reviewsAreListedNewestFirst() throws Exception {
        when(reviewService.reviewsFor("e1")).thenReturn(List.of(
                review("r2", 4, LocalDateTime.of(2030, 1, 6, 9, 0)),
                review("r1", 5, LocalDateTime.of(2030, 1, 5, 10, 30))));

        mockMvc.perform(get("/events/e1/reviews"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("r2"))
                .andExpect(jsonPath("$[0].createdAt").value("2030-01-06T09:00:00"))
                .andExpect(jsonPath("$[1].id").value("r1"))
                .andExpect(jsonPath("$[1].createdAt").value("2030-01-05T10:30:00"));
    }

    @Test
    void ratingOfAKnownEventIsReturned() throws Exception {
        when(reviewService.ratingFor("e1")).thenReturn(Optional.of(new RatingSummary("e1", 4.5, 2)));

        mockMvc.perform(get("/events/e1/rating"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eventId").value("e1"))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(2));
    }

    @Test
    void ratingOfAnUnknownEventIsNotFoundProblemDetail() throws Exception {
        when(reviewService.ratingFor("e9")).thenReturn(Optional.empty());

        mockMvc.perform(get("/events/e9/rating"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("e9")));
    }

    private void assertInvalidReview(String body) throws Exception {
        mockMvc.perform(post("/events/e1/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(reviewService, never()).addReview(anyString(), anyString(), anyInt(), any());
    }
}
