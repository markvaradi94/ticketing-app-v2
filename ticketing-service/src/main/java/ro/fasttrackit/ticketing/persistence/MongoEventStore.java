package ro.fasttrackit.ticketing.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.service.port.EventStore;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MongoEventStore implements EventStore {

    private final EventRepository events;
    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<Event> findById(String id) {
        return events.findById(id).map(EventDocument::toDomain);
    }

    @Override
    public List<Event> findAll() {
        return toDomain(events.findAll());
    }

    @Override
    public void insert(Event event) {
        events.insert(EventDocument.from(event));
    }

    @Override
    public boolean existsById(String id) {
        return events.existsById(id);
    }

    @Override
    public List<Event> findByCityIgnoreCase(String city) {
        return toDomain(events.findByVenueCityIgnoreCase(city));
    }

    @Override
    public List<Event> topByBookedSeats(int n) {
        Query query = new Query()
                .with(Sort.by(Sort.Order.desc("bookedSeats"), Sort.Order.asc("name"), Sort.Order.asc("_id")))
                .limit(n);
        return toDomain(mongoTemplate.find(query, EventDocument.class));
    }

    // The @Version field makes a stale save fail; only this adapter knows Spring's exception for it.
    @Override
    public boolean trySave(Event event) {
        try {
            events.save(EventDocument.from(event));
            return true;
        } catch (OptimisticLockingFailureException lostTheRace) {
            return false;
        }
    }

    private static List<Event> toDomain(List<EventDocument> documents) {
        return documents.stream()
                .map(EventDocument::toDomain)
                .toList();
    }
}
