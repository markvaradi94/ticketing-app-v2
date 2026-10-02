package ro.fasttrackit.ticketing.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.TicketOffice;
import ro.fasttrackit.ticketing.domain.Venue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller test ({@code @WebMvcTest}, no Spring context beyond MVC): checks {@link EventController}
 * against a mocked {@link TicketOffice}.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketOffice ticketOffice;

    private static Event event(String id, String name, String city, LocalDateTime startsAt, int capacity, int bookedSeats) {
        return Event.builder()
                .id(id)
                .name(name)
                .venue(new Venue("Arena", city))
                .startsAt(startsAt)
                .capacity(capacity)
                .bookedSeats(bookedSeats)
                .build();
    }

    @Test
    void listsAllEvents() throws Exception {
        when(ticketOffice.allEvents()).thenReturn(List.of(
                event("e1", "Concert", "Cluj", LocalDateTime.of(2030, 6, 12, 20, 0), 100, 30),
                event("e2", "Play", "Iasi", LocalDateTime.of(2030, 7, 3, 19, 0), 50, 0)));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("e1"))
                .andExpect(jsonPath("$[0].name").value("Concert"))
                .andExpect(jsonPath("$[0].venueName").value("Arena"))
                .andExpect(jsonPath("$[0].city").value("Cluj"))
                .andExpect(jsonPath("$[0].startsAt").value("2030-06-12T20:00:00"))
                .andExpect(jsonPath("$[0].capacity").value(100))
                .andExpect(jsonPath("$[0].availableSeats").value(70))
                .andExpect(jsonPath("$[1].id").value("e2"));
    }

    @Test
    void emptyOfficeListsNoEvents() throws Exception {
        when(ticketOffice.allEvents()).thenReturn(List.of());

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsEventById() throws Exception {
        when(ticketOffice.findEvent("e1")).thenReturn(Optional.of(
                event("e1", "Concert", "Cluj", LocalDateTime.of(2030, 6, 12, 20, 0), 100, 30)));

        mockMvc.perform(get("/events/e1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("e1"))
                .andExpect(jsonPath("$.city").value("Cluj"))
                .andExpect(jsonPath("$.startsAt").value("2030-06-12T20:00:00"))
                .andExpect(jsonPath("$.availableSeats").value(70));
    }

    @Test
    void unknownIdIsNotFoundProblemDetail() throws Exception {
        when(ticketOffice.findEvent("e9")).thenReturn(Optional.empty());

        mockMvc.perform(get("/events/e9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("e9")));
    }
}
