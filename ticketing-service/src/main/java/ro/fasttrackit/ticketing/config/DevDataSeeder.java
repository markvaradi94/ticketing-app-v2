package ro.fasttrackit.ticketing.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ro.fasttrackit.ticketing.persistence.EventDocument;
import ro.fasttrackit.ticketing.persistence.EventRepository;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final TicketingProperties properties;
    private final EventRepository events;

    @Override
    public void run(ApplicationArguments args) {
        if (events.count() > 0) {
            return;
        }
        properties.seedEvents().stream()
                .map(TicketingProperties.SeedEvent::toDomain)
                .map(EventDocument::from)
                .forEach(events::save);
    }
}
