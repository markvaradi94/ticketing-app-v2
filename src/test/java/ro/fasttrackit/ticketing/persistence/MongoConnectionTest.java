package ro.fasttrackit.ticketing.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Persistence test (real MongoDB in a Testcontainer): checks that the app can write to and read from MongoDB.
 */
@DataMongoTest
@Testcontainers
class MongoConnectionTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    record Ping(String id, String message) {
    }

    @Test
    void savesAndReadsADocument() {
        mongoTemplate.save(new Ping("p1", "hello"));

        assertEquals("hello", mongoTemplate.findById("p1", Ping.class).message());
    }
}
