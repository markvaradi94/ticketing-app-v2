package ro.fasttrackit.ticketing.messaging;

import org.slf4j.MDC;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ro.fasttrackit.ticketing.web.CorrelationIdFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ticketing declares only the exchange it publishes to. Each consumer declares its own queue and binding.
 */
@Configuration
public class MessagingConfig {

    public static final String EXCHANGE = "ticketing.events";
    public static final String BOOKING_CONFIRMED = "booking.confirmed";

    @Bean
    TopicExchange ticketingEvents() {
        return new TopicExchange(EXCHANGE);
    }

    // Messages are JSON, written by the same JsonMapper as the HTTP API (dates as ISO-8601 strings).
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    // The queue is notification-service's only inbound link, so the request's correlation id travels as a message
    // header, under the same name as the HTTP header.
    @Bean
    RabbitTemplateCustomizer correlationIdHeader() {
        return template -> template.addBeforePublishPostProcessors(message -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                message.getMessageProperties().setHeader(CorrelationIdFilter.HEADER, correlationId);
            }
            return message;
        });
    }
}
