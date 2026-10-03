package ro.fasttrackit.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test (full Spring context on RabbitMQ and MongoDB Testcontainers): messages are published to
 * ticketing's exchange as raw JSON, exactly as ticketing sends them (with its {@code __TypeId__} header), and the
 * listener consumes them. A duplicated delivery stores one notification; an unreadable or invalid message lands in
 * the dead-letter queue. The correlation id in a message's header shows up in the log lines it causes, and not in
 * the next message's.
 */
@SpringBootTest
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class BookingConfirmedListenerTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management");

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private NotificationRepository notifications;

    @BeforeEach
    void startEmpty() {
        amqpAdmin.purgeQueue(MessagingConfig.DEAD_LETTER_QUEUE, false);
        notifications.deleteAll();
    }

    private static String bookingConfirmed(String bookingId, int seats) {
        return """
                {"bookingId":"%s","eventId":"rock-cluj","eventName":"Rock Night","eventStartsAt":"2030-06-12T20:00:00",
                 "customerEmail":"ana@mail.ro","seats":%d,"bookedAt":"2026-10-28T18:30:00.123"}
                """.formatted(bookingId, seats);
    }

    private void publish(String json) {
        publish(json, null);
    }

    private void publish(String json, String correlationId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "ro.fasttrackit.ticketing.domain.BookingConfirmed");
        if (correlationId != null) {
            properties.setHeader(CorrelationIdFilter.HEADER, correlationId);
        }
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);
        rabbitTemplate.send(MessagingConfig.EXCHANGE, MessagingConfig.BOOKING_CONFIRMED, message);
    }

    private static String lineAbout(CapturedOutput output, String bookingId) {
        return output.getOut().lines()
                .filter(line -> line.contains("Received BookingConfirmed for booking " + bookingId))
                .findFirst()
                .orElse("");
    }

    @Test
    void storesOneNotificationWithReadableText() {
        publish(bookingConfirmed("b-1", 2));

        await().atMost(WAIT).until(() -> notifications.existsById("b-1"));
        Notification notification = notifications.findById("b-1").orElseThrow();
        assertEquals("ana@mail.ro", notification.customerEmail());
        assertEquals("2 seat(s) for Rock Night on 12 Jun 2030 at 20:00 are confirmed.", notification.text());
    }

    @Test
    void duplicatedDeliveryStoresOneNotificationAndIsNotDeadLettered() {
        publish(bookingConfirmed("b-1", 2));
        publish(bookingConfirmed("b-1", 2));
        // One listener handles the queue in order, so once b-2 is stored both copies of b-1 were handled.
        publish(bookingConfirmed("b-2", 1));

        await().atMost(WAIT).until(() -> notifications.existsById("b-2"));
        assertEquals(2, notifications.count());
        assertNull(rabbitTemplate.receive(MessagingConfig.DEAD_LETTER_QUEUE, 500), "the duplicate was dead-lettered");
    }

    @Test
    void logsTheMessagesCorrelationIdAndDoesNotCarryItToTheNextMessage(CapturedOutput output) {
        publish(bookingConfirmed("b-1", 2), "corr-from-ticketing");
        publish(bookingConfirmed("b-2", 1));

        await().atMost(WAIT).until(() -> !lineAbout(output, "b-2").isEmpty());
        assertTrue(lineAbout(output, "b-1").contains("[corr-from-ticketing]"), lineAbout(output, "b-1"));
        assertFalse(lineAbout(output, "b-2").contains("corr-from-ticketing"), lineAbout(output, "b-2"));
    }

    @Test
    void unreadableMessageLandsInTheDeadLetterQueue() {
        publish("this is not JSON");

        Message deadLetter = rabbitTemplate.receive(MessagingConfig.DEAD_LETTER_QUEUE, WAIT.toMillis());
        assertNotNull(deadLetter, "nothing in the dead-letter queue");
        assertEquals("this is not JSON", new String(deadLetter.getBody(), StandardCharsets.UTF_8));
        assertEquals(0, notifications.count());
    }

    @Test
    void invalidMessageLandsInTheDeadLetterQueue() {
        publish(bookingConfirmed("b-1", 0));

        assertNotNull(rabbitTemplate.receive(MessagingConfig.DEAD_LETTER_QUEUE, WAIT.toMillis()),
                "nothing in the dead-letter queue");
        assertEquals(0, notifications.count());
    }
}
