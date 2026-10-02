package ro.fasttrackit.ticketing.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.TicketOffice;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final TicketOffice ticketOffice;

    @GetMapping
    public List<EventResponse> allEvents(@RequestParam(required = false) String city) {
        List<Event> events = city == null
                ? ticketOffice.allEvents()
                : ticketOffice.upcomingEventsIn(city, LocalDateTime.now());
        return toResponses(events);
    }

    @GetMapping("/top")
    public List<EventResponse> topEvents(@RequestParam(defaultValue = "3") @Positive int n) {
        return toResponses(ticketOffice.topEventsByBookedSeats(n));
    }

    @GetMapping("/{id}")
    public EventResponse event(@PathVariable String id) {
        return ticketOffice.findEvent(id)
                .map(EventResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No event with id " + id));
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest body) {
        Event event = body.toDomain(UUID.randomUUID().toString());
        ticketOffice.addEvent(event);
        return ResponseEntity.created(URI.create("/events/" + event.getId()))
                .body(EventResponse.from(event));
    }

    private static List<EventResponse> toResponses(List<Event> events) {
        return events.stream()
                .map(EventResponse::from)
                .toList();
    }
}
