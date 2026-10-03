package ro.fasttrackit.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link BookingConfirmed}. A message that isn't valid JSON fails in the converter; one that is JSON but
 * can't be acted on is rejected here. Both go to the dead-letter queue instead of being redelivered forever.
 */
@Component
@RequiredArgsConstructor
public class BookingConfirmedListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = MessagingConfig.QUEUE)
    public void on(BookingConfirmed event) {
        if (!event.isValid()) {
            throw new AmqpRejectAndDontRequeueException("Invalid BookingConfirmed: " + event.bookingId());
        }
        notificationService.store(event);
    }
}
