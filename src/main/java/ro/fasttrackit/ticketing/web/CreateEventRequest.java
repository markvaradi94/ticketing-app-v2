package ro.fasttrackit.ticketing.web;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record CreateEventRequest(@NotBlank String name, @NotBlank String venueName, @NotBlank String city,
                                 @NotNull @Future LocalDateTime startsAt, @Positive int capacity) {

    public Event toDomain(String id) {
        return Event.builder()
                .id(id)
                .name(name)
                .venue(new Venue(venueName, city))
                .startsAt(startsAt.truncatedTo(ChronoUnit.MILLIS))
                .capacity(capacity)
                .bookedSeats(0)
                .build();
    }
}
