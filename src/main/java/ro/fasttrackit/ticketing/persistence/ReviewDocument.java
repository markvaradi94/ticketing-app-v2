package ro.fasttrackit.ticketing.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import ro.fasttrackit.ticketing.domain.Review;

import java.time.LocalDateTime;

@Document("reviews")
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")
public class ReviewDocument {
    @Id
    private String id;
    @Indexed
    private String eventId;
    private String author;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;

    public static ReviewDocument from(Review review) {
        return ReviewDocument.builder()
                .id(review.getId())
                .eventId(review.getEventId())
                .author(review.getAuthor())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    public Review toDomain() {
        return Review.builder()
                .id(id)
                .eventId(eventId)
                .author(author)
                .rating(rating)
                .comment(comment)
                .createdAt(createdAt)
                .build();
    }
}
