package ro.fasttrackit.ticketing.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TicketOffice {

    private final Map<String, Event> events;
    private final List<Booking> bookings = new ArrayList<>();

    public TicketOffice(List<Event> events) {
        this.events = events.stream()
                .collect(Collectors.toMap(Event::getId, Function.identity(), (first, second) -> {
                    throw new IllegalArgumentException("Duplicate event id " + first.getId());
                }, LinkedHashMap::new));
    }

    public BookingResult book(BookingRequest request) {
        Event event = events.get(request.eventId());
        if (event == null) {
            return new BookingResult.UnknownEvent(request.eventId());
        }
        if (!event.getStartsAt().isAfter(LocalDateTime.now())) {
            return new BookingResult.AlreadyStarted(event.getId(), event.getStartsAt());
        }
        if (request.seats() > event.availableSeats()) {
            return new BookingResult.SoldOut(event.getId(), event.availableSeats());
        }

        Event updated = event.toBuilder()
                .bookedSeats(event.getBookedSeats() + request.seats())
                .build();
        events.put(updated.getId(), updated);

        Booking booking = Booking.builder()
                .id(UUID.randomUUID().toString())
                .eventId(event.getId())
                .customerEmail(request.customerEmail())
                .seats(request.seats())
                .bookedAt(LocalDateTime.now())
                .build();
        bookings.add(booking);

        return new BookingResult.Confirmed(booking);
    }

    public Optional<Event> findEvent(String id) {
        return Optional.ofNullable(events.get(id));
    }

    public List<Event> allEvents() {
        return events.values().stream().toList();
    }

    public List<Event> eventsWithFreeSeats() {
        return events.values().stream()
                .filter(event -> event.availableSeats() > 0)
                .toList();
    }

    public Map<String, Integer> bookedSeatsPerEvent() {
        return bookings.stream()
                .collect(Collectors.groupingBy(Booking::getEventId, Collectors.summingInt(Booking::getSeats)));
    }

    public List<Event> topEventsByBookedSeats(int n) {
        return events.values().stream()
                .sorted(Comparator.comparingInt(Event::getBookedSeats).reversed()
                        .thenComparing(Event::getName))
                .limit(n)
                .toList();
    }

    public List<Event> upcomingEventsIn(String city, LocalDateTime now) {
        return events.values().stream()
                .filter(event -> event.getVenue().city().equalsIgnoreCase(city))
                .filter(event -> event.getStartsAt().isAfter(now))
                .sorted(Comparator.comparing(Event::getStartsAt))
                .toList();
    }
}
