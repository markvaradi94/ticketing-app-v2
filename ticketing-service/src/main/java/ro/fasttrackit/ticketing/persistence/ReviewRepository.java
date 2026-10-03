package ro.fasttrackit.ticketing.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReviewRepository extends MongoRepository<ReviewDocument, String> {

    List<ReviewDocument> findByEventIdOrderByCreatedAtDesc(String eventId);
}
