package ro.fasttrackit.ticketing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;

import java.time.LocalDateTime;
import java.util.List;

@ConfigurationProperties("ticketing")
public record TicketingProperties(List<SeedEvent> seedEvents) {

    public TicketingProperties {
        seedEvents = seedEvents == null ? List.of() : List.copyOf(seedEvents);
    }

    public record SeedEvent(String id, String name, String venueName, String city, LocalDateTime startsAt, int capacity) {

        public Event toDomain() {
            return Event.builder()
                    .id(id)
                    .name(name)
                    .venue(new Venue(venueName, city))
                    .startsAt(startsAt)
                    .capacity(capacity)
                    .bookedSeats(0)
                    .build();
        }
    }
}
