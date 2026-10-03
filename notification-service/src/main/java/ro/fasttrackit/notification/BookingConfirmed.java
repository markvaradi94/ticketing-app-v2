package ro.fasttrackit.notification;

import java.time.LocalDateTime;

/**
 * notification-service's own copy of the message ticketing publishes. The services share no code: the contract is
 * the JSON, so this record only has to match its field names.
 */
public record BookingConfirmed(
        String bookingId,
        String eventId,
        String eventName,
        LocalDateTime eventStartsAt,
        String customerEmail,
        int seats,
        LocalDateTime bookedAt) {

    /** A message we can act on: it names the booking, the customer and the event, and books at least one seat. */
    public boolean isValid() {
        return hasText(bookingId) && hasText(customerEmail) && hasText(eventName) && eventStartsAt != null && seats > 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
