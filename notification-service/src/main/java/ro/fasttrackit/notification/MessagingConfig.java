package ro.fasttrackit.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * notification-service declares what it consumes from: its queue, the binding to ticketing's exchange, and its
 * dead-letter exchange and queue. It declares the exchange too, with the same name and type as ticketing, because a
 * binding needs the exchange to exist and either service may start first.
 */
@Configuration
public class MessagingConfig {

    public static final String EXCHANGE = "ticketing.events";
    public static final String BOOKING_CONFIRMED = "booking.confirmed";
    public static final String QUEUE = "notification.booking-confirmed";
    public static final String DEAD_LETTER_EXCHANGE = "notification.dlx";
    public static final String DEAD_LETTER_QUEUE = "notification.booking-confirmed.dlq";

    @Bean
    TopicExchange ticketingEvents() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue bookingConfirmedQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Binding bookingConfirmedBinding() {
        return BindingBuilder.bind(bookingConfirmedQueue()).to(ticketingEvents()).with(BOOKING_CONFIRMED);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_QUEUE);
    }

    // The publisher's __TypeId__ header names ticketing's class, which doesn't exist here. A @RabbitListener
    // converts to its parameter type by default, so the header is ignored and our own BookingConfirmed is used.
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
