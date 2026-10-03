package ro.fasttrackit.ticketing.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;
import ro.fasttrackit.ticketing.service.TicketOffice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration test (full Spring context on RabbitMQ and MongoDB Testcontainers): a confirmed booking puts exactly
 * one {@code BookingConfirmed} JSON message on the {@code ticketing.events} exchange. The test binds its own queue,
 * as any consumer would, and checks the JSON field by field: that JSON is the contract with other services.
 */
@SpringBootTest
@Testcontainers
class RabbitBookingEventPublisherTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management");

    private static final String QUEUE = "test.booking-confirmed";
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2030, 6, 12, 20, 0);

    @Autowired
    private TicketOffice office;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void bindATestQueue() {
        Queue testQueue = new Queue(QUEUE);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.purgeQueue(QUEUE, false);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue)
                .to(new TopicExchange(MessagingConfig.EXCHANGE))
                .with(MessagingConfig.BOOKING_CONFIRMED));
    }

    @Test
    void confirmedBookingPublishesOneJsonMessageWithTheAgreedFields() {
        office.addEvent(Event.builder()
                .id("rock-cluj")
                .name("Rock Night")
                .venue(new Venue("BT Arena", "Cluj"))
                .startsAt(STARTS_AT)
                .capacity(10)
                .bookedSeats(0)
                .build());

        BookingResult result = office.book(new BookingRequest("rock-cluj", "ana@mail.ro", 2));
        Booking booking = assertInstanceOf(BookingResult.Confirmed.class, result).booking();

        Message message = rabbitTemplate.receive(QUEUE, 5_000);
        assertNotNull(message, "no message published");
        assertEquals("application/json", message.getMessageProperties().getContentType());
        JsonNode json = jsonMapper.readTree(message.getBody());
        assertEquals(booking.getId(), json.get("bookingId").asString());
        assertEquals("rock-cluj", json.get("eventId").asString());
        assertEquals("Rock Night", json.get("eventName").asString());
        assertEquals(STARTS_AT, LocalDateTime.parse(json.get("eventStartsAt").asString()));
        assertEquals("ana@mail.ro", json.get("customerEmail").asString());
        assertEquals(2, json.get("seats").asInt());
        assertEquals(booking.getBookedAt(), LocalDateTime.parse(json.get("bookedAt").asString()));

        assertNull(rabbitTemplate.receive(QUEUE, 500), "more than one message published");
    }
}
