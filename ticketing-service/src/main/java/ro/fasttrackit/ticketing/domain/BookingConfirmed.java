package ro.fasttrackit.ticketing.domain;

import java.time.LocalDateTime;

/**
 * The fact that a booking was confirmed and saved. It carries what other services need to know about the booking,
 * including the event's name and start, so a consumer never has to call back into ticketing. It can cross a
 * service boundary.
 */
public record BookingConfirmed(
        String bookingId,
        String eventId,
        String eventName,
        LocalDateTime eventStartsAt,
        String customerEmail,
        int seats,
        LocalDateTime bookedAt) {

    public static BookingConfirmed from(Booking booking, Event event) {
        return new BookingConfirmed(
                booking.getId(),
                event.getId(),
                event.getName(),
                event.getStartsAt(),
                booking.getCustomerEmail(),
                booking.getSeats(),
                booking.getBookedAt());
    }
}
