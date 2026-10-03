package ro.fasttrackit.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.persistence.BookingDocument;
import ro.fasttrackit.ticketing.persistence.BookingRepository;
import ro.fasttrackit.ticketing.persistence.EventDocument;
import ro.fasttrackit.ticketing.persistence.EventRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketOffice {

    private final EventRepository events;
    private final BookingRepository bookings;

    public BookingResult book(BookingRequest request) {
        EventDocument doc = events.findById(request.eventId()).orElse(null);
        if (doc == null) {
            return new BookingResult.UnknownEvent(request.eventId());
        }
        Event event = doc.toDomain();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        if (!event.getStartsAt().isAfter(now)) {
            return new BookingResult.AlreadyStarted(event.getId(), event.getStartsAt());
        }
        if (request.seats() > event.availableSeats()) {
            return new BookingResult.SoldOut(event.getId(), event.availableSeats());
        }

        Event updated = event.toBuilder()
                .bookedSeats(event.getBookedSeats() + request.seats())
                .build();
        events.save(EventDocument.from(updated));

        Booking booking = Booking.builder()
                .id(UUID.randomUUID().toString())
                .eventId(event.getId())
                .customerEmail(request.customerEmail())
                .seats(request.seats())
                .bookedAt(now)
                .build();
        bookings.save(BookingDocument.from(booking));

        return new BookingResult.Confirmed(booking);
    }

    public void addEvent(Event event) {
        if (events.existsById(event.getId())) {
            throw new IllegalArgumentException("Duplicate event id " + event.getId());
        }
        events.insert(EventDocument.from(event));
    }

    public Optional<Booking> findBooking(String id) {
        return bookings.findById(id).map(BookingDocument::toDomain);
    }

    public Optional<Event> findEvent(String id) {
        return events.findById(id).map(EventDocument::toDomain);
    }

    public List<Event> allEvents() {
        return events.findAll().stream()
                .map(EventDocument::toDomain)
                .sorted(Comparator.comparing(Event::getStartsAt))
                .toList();
    }

    public List<Event> eventsWithFreeSeats() {
        return allEvents().stream()
                .filter(event -> event.availableSeats() > 0)
                .toList();
    }

    public Map<String, Integer> bookedSeatsPerEvent() {
        return bookings.findAll().stream()
                .map(BookingDocument::toDomain)
                .collect(Collectors.groupingBy(Booking::getEventId, Collectors.summingInt(Booking::getSeats)));
    }

    public List<Event> topEventsByBookedSeats(int n) {
        return events.findAll().stream()
                .map(EventDocument::toDomain)
                .sorted(Comparator.comparingInt(Event::getBookedSeats).reversed()
                        .thenComparing(Event::getName))
                .limit(n)
                .toList();
    }

    public List<Event> upcomingEventsIn(String city, LocalDateTime now) {
        return events.findByVenueCityIgnoreCase(city).stream()
                .map(EventDocument::toDomain)
                .filter(event -> event.getStartsAt().isAfter(now))
                .sorted(Comparator.comparing(Event::getStartsAt))
                .toList();
    }
}
