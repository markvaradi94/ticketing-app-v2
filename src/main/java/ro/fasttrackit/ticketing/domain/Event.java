package ro.fasttrackit.ticketing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")
public class Event {
    private String id;
    private String name;
    private Venue venue;
    private LocalDateTime startsAt;
    private int capacity;
    private int bookedSeats;

    public int availableSeats() {
        return capacity - bookedSeats;
    }
}
