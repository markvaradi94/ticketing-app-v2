package ro.fasttrackit.ticketing.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EventRepository extends MongoRepository<EventDocument, String> {

    List<EventDocument> findByVenueCityIgnoreCase(String city);
}
