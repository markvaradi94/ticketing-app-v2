package ro.fasttrackit.ticketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.TicketOffice;
import ro.fasttrackit.ticketing.domain.Venue;

import java.util.List;

@Configuration
public class TicketingConfig {

    @Bean
    TicketOffice ticketOffice(TicketingProperties properties) {
        List<Event> events = properties.seedEvents().stream()
                .map(TicketingConfig::toEvent)
                .toList();
        return new TicketOffice(events);
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
