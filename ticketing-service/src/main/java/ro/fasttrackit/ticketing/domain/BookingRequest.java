package ro.fasttrackit.ticketing.domain;

public record BookingRequest(String eventId, String customerEmail, int seats) {

    public BookingRequest {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Event id must not be blank");
        }
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("Customer email must not be blank");
        }
        if (seats <= 0) {
            throw new IllegalArgumentException("Seats must be greater than 0");
        }
    }
}
