package ro.fasttrackit.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test (full Spring context on MongoDB and RabbitMQ Testcontainers): checks that the application starts,
 * creates its indexes, and reports MongoDB and RabbitMQ healthy through Actuator.
 */
@SpringBootTest
@Testcontainers
class TicketingApplicationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void contextLoads() {
    }

    @Test
    void healthShowsMongoDbAndRabbitMqUp() {
        assertEquals(Status.UP, healthEndpoint.healthForPath("mongo").getStatus());
        assertEquals(Status.UP, healthEndpoint.healthForPath("rabbit").getStatus());
        assertEquals(Status.UP, healthEndpoint.health().getStatus());
    }

    @Test
    void reviewsAreIndexedByEventId() {
        List<IndexInfo> indexes = mongoTemplate.indexOps("reviews").getIndexInfo();
        assertTrue(indexes.stream().anyMatch(index -> index.isIndexForFields(List.of("eventId"))),
                () -> "expected an index on reviews.eventId, found index keys: " + indexes.stream()
                        .map(index -> index.getIndexFields().stream().map(IndexField::getKey).toList())
                        .toList());
    }
}
