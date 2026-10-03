package ro.fasttrackit.ticketing.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookingRepository extends MongoRepository<BookingDocument, String> {
}
