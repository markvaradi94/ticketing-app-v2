package ro.fasttrackit.ticketing.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ro.fasttrackit.ticketing.domain.BookingConfirmed;
import ro.fasttrackit.ticketing.service.port.BookingEventPublisher;

/**
 * Publishes {@link BookingConfirmed} as JSON to the {@code ticketing.events} exchange with the routing key
 * {@code booking.confirmed}. A message published while no queue is bound to that key is dropped by the broker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitBookingEventPublisher implements BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(BookingConfirmed event) {
        rabbitTemplate.convertAndSend(MessagingConfig.EXCHANGE, MessagingConfig.BOOKING_CONFIRMED, event);
        log.info("Published BookingConfirmed for booking {}", event.bookingId());
    }
}
