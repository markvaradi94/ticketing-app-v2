package ro.fasttrackit.ticketing.domain;

import java.time.LocalDateTime;

/**
 * The fact that a booking was confirmed and saved. It carries only what other services need to know about the
 * booking, so it can cross a service boundary.
 */
public record BookingConfirmed(String bookingId, String eventId, String customerEmail, int seats, LocalDateTime bookedAt) {

    public static BookingConfirmed from(Booking booking) {
        return new BookingConfirmed(
                booking.getId(),
                booking.getEventId(),
                booking.getCustomerEmail(),
                booking.getSeats(),
                booking.getBookedAt());
    }
}
