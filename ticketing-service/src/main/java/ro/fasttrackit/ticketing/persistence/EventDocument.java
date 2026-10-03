package ro.fasttrackit.ticketing.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;

import java.time.LocalDateTime;

@Document("events")
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")
public class EventDocument {
    @Id
    private String id;
    private String name;
    private Venue venue;
    private LocalDateTime startsAt;
    private int capacity;
    private int bookedSeats;
    @Version
    private Long version;

    public static EventDocument from(Event event) {
        return EventDocument.builder()
                .id(event.getId())
                .name(event.getName())
                .venue(event.getVenue())
                .startsAt(event.getStartsAt())
                .capacity(event.getCapacity())
                .bookedSeats(event.getBookedSeats())
                .version(event.getVersion())
                .build();
    }

    public Event toDomain() {
        return Event.builder()
                .id(id)
                .name(name)
                .venue(venue)
                .startsAt(startsAt)
                .capacity(capacity)
                .bookedSeats(bookedSeats)
                .version(version)
                .build();
    }
}
