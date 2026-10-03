package ro.fasttrackit.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link BookingConfirmed}. A message that isn't valid JSON fails in the converter; one that is JSON but
 * can't be acted on is rejected here. Both go to the dead-letter queue instead of being redelivered forever.
 * The booking request's correlation id arrives in a message header and is logged with everything this message
 * causes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingConfirmedListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = MessagingConfig.QUEUE)
    public void on(BookingConfirmed event,
                   @Header(name = CorrelationIdFilter.HEADER, required = false) String correlationId) {
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            log.info("Received BookingConfirmed for booking {}", event.bookingId());
            if (!event.isValid()) {
                throw new AmqpRejectAndDontRequeueException("Invalid BookingConfirmed: " + event.bookingId());
            }
            notificationService.store(event);
        } finally {
            // The listener thread handles the next message too: it must not inherit this id.
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
