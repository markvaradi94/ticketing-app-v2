package ro.fasttrackit.ticketing.web;

import ro.fasttrackit.ticketing.domain.Event;

import java.time.LocalDateTime;

public record EventResponse(String id, String name, String venueName, String city, LocalDateTime startsAt,
                            int capacity, int availableSeats) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getVenue().name(),
                event.getVenue().city(),
                event.getStartsAt(),
                event.getCapacity(),
                event.availableSeats());
    }
}
