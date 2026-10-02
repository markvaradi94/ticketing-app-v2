package ro.fasttrackit.ticketing.domain;

public sealed interface BookingResult {

    record Confirmed(Booking booking) implements BookingResult {
    }

    record SoldOut(String eventId, int availableSeats) implements BookingResult {
    }

    record UnknownEvent(String eventId) implements BookingResult {
    }

    default String describe() {
        return switch (this) {
            case Confirmed(Booking booking) -> "Booking %s confirmed: %d seats".formatted(booking.getId(), booking.getSeats());
            case SoldOut(String eventId, int availableSeats) -> "Event %s has only %d seats left".formatted(eventId, availableSeats);
            case UnknownEvent(String eventId) -> "No event with id %s".formatted(eventId);
        };
    }
}
