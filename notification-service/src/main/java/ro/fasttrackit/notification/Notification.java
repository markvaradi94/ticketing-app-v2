package ro.fasttrackit.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One notification per booking: its id is the booking id, so a redelivered {@link BookingConfirmed} can't store a
 * second one.
 */
@Document("notifications")
public record Notification(@Id String bookingId, String customerEmail, String text, LocalDateTime notifiedAt) {
}
