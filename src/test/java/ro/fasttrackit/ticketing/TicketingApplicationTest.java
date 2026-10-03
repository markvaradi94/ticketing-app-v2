package ro.fasttrackit.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test (full Spring context on a MongoDB Testcontainer): checks that the application starts and
 * creates its indexes.
 */
@SpringBootTest
@Testcontainers
class TicketingApplicationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void reviewsAreIndexedByEventId() {
        assertTrue(mongoTemplate.indexOps("reviews").getIndexInfo().stream()
                .anyMatch(index -> index.isIndexForFields(List.of("eventId"))));
    }
}
