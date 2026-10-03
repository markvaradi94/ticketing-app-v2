package ro.fasttrackit.ticketing.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.BookingResult.AlreadyStarted;
import ro.fasttrackit.ticketing.domain.BookingResult.Confirmed;
import ro.fasttrackit.ticketing.domain.BookingResult.SoldOut;
import ro.fasttrackit.ticketing.domain.BookingResult.UnknownEvent;
import ro.fasttrackit.ticketing.service.TicketOffice;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/events/{eventId}/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final TicketOffice ticketOffice;

    @PostMapping
    public ResponseEntity<?> book(@PathVariable String eventId, @Valid @RequestBody BookingRequestBody body) {
        return switch (ticketOffice.book(body.toDomain(eventId))) {
            case Confirmed(Booking booking) -> ResponseEntity.status(CREATED).body(BookingResponse.from(booking));
            case UnknownEvent(String id) -> problem(NOT_FOUND, "No event with id " + id, Map.of());
            case SoldOut(String id, int available) ->
                    problem(CONFLICT, "Event " + id + " has only " + available + " seats left", Map.of("availableSeats", available));
            case AlreadyStarted(String id, LocalDateTime startsAt) ->
                    problem(CONFLICT, "Event " + id + " already started", Map.of("startsAt", startsAt));
        };
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<?> booking(@PathVariable String eventId, @PathVariable String bookingId) {
        return ticketOffice.findBooking(bookingId)
                .filter(booking -> booking.getEventId().equals(eventId))
                .<ResponseEntity<?>>map(booking -> ResponseEntity.ok(BookingResponse.from(booking)))
                .orElseGet(() -> problem(NOT_FOUND, "No booking with id " + bookingId + " for event " + eventId, Map.of()));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, Map<String, Object> properties) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        properties.forEach(problemDetail::setProperty);
        return ResponseEntity.of(problemDetail).build();
    }
}
