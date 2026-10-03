package ro.fasttrackit.ticketing.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Component;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.service.port.BookingStore;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MongoBookingStore implements BookingStore {

    private final BookingRepository bookings;
    private final MongoTemplate mongoTemplate;

    @Override
    public void save(Booking booking) {
        bookings.save(BookingDocument.from(booking));
    }

    @Override
    public Optional<Booking> findById(String id) {
        return bookings.findById(id).map(BookingDocument::toDomain);
    }

    @Override
    public Map<String, Integer> bookedSeatsPerEvent() {
        Aggregation pipeline = Aggregation.newAggregation(
                Aggregation.group("eventId").sum("seats").as("seats"));
        return mongoTemplate.aggregate(pipeline, BookingDocument.class, SeatsPerEvent.class)
                .getMappedResults().stream()
                .collect(Collectors.toMap(SeatsPerEvent::id, SeatsPerEvent::seats));
    }

    // One $group result: the event id (MongoDB's _id) and the summed seats.
    private record SeatsPerEvent(String id, int seats) {
    }
}
