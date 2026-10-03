package ro.fasttrackit.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter STARTS_AT = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.ENGLISH);

    private final NotificationRepository notifications;

    /**
     * Stores the booking's notification once. Delivery is at-least-once, so the same booking can arrive again: the
     * insert then fails on the booking id, and that means the work is already done.
     */
    public void store(BookingConfirmed event) {
        Notification notification = new Notification(
                event.bookingId(),
                event.customerEmail(),
                text(event),
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            notifications.insert(notification);
        } catch (DuplicateKeyException alreadyNotified) {
            log.debug("Booking {} was already notified; duplicate delivery ignored", event.bookingId());
        }
    }

    public List<Notification> latest() {
        return notifications.findAll(Sort.by(Sort.Direction.DESC, "notifiedAt"));
    }

    private static String text(BookingConfirmed event) {
        return "%d seat(s) for %s on %s are confirmed.".formatted(
                event.seats(), event.eventName(), STARTS_AT.format(event.eventStartsAt()));
    }
}
