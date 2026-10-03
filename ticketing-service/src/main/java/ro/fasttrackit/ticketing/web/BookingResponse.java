package ro.fasttrackit.ticketing.web;

import ro.fasttrackit.ticketing.domain.Booking;

import java.time.LocalDateTime;

public record BookingResponse(String id, String eventId, String customerEmail, int seats, LocalDateTime bookedAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getEventId(),
                booking.getCustomerEmail(),
                booking.getSeats(),
                booking.getBookedAt());
    }
}
