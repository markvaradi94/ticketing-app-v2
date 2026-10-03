package ro.fasttrackit.ticketing.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;
import ro.fasttrackit.ticketing.persistence.EventDocument;
import ro.fasttrackit.ticketing.persistence.EventRepository;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class TicketingConfig implements ApplicationRunner {

    private final TicketingProperties properties;
    private final EventRepository events;

    @Override
    public void run(ApplicationArguments args) {
        properties.seedEvents().stream()
                .map(TicketingConfig::toEvent)
                .map(EventDocument::from)
                .forEach(events::save);
    }

    private static Event toEvent(TicketingProperties.SeedEvent seed) {
        return Event.builder()
                .id(seed.id())
                .name(seed.name())
                .venue(new Venue(seed.venueName(), seed.city()))
                .startsAt(seed.startsAt())
                .capacity(seed.capacity())
                .bookedSeats(0)
                .build();
    }
}
