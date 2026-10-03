package ro.fasttrackit.ticketing.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.service.TicketOffice;
import ro.fasttrackit.ticketing.domain.Venue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Test
    void cityQueryReturnsUpcomingEventsInCity() throws Exception {
        when(ticketOffice.upcomingEventsIn(eq("cluj"), any(LocalDateTime.class))).thenReturn(List.of(
                event("e1", "Concert", "Cluj", LocalDateTime.of(2030, 6, 12, 20, 0), 100, 30)));

        mockMvc.perform(get("/events").param("city", "cluj"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("e1"))
                .andExpect(jsonPath("$[0].city").value("Cluj"));

        verify(ticketOffice, never()).allEvents();
    }

    @Test
    void noCityListsAllEvents() throws Exception {
        when(ticketOffice.allEvents()).thenReturn(List.of());

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk());

        verify(ticketOffice).allEvents();
        verify(ticketOffice, never()).upcomingEventsIn(any(), any());
    }

    @Test
    void blankCityListsAllEvents() throws Exception {
        when(ticketOffice.allEvents()).thenReturn(List.of(
                event("e1", "Concert", "Cluj", LocalDateTime.of(2030, 6, 12, 20, 0), 100, 30)));

        mockMvc.perform(get("/events").param("city", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("e1"));

        verify(ticketOffice).allEvents();
        verify(ticketOffice, never()).upcomingEventsIn(any(), any());
    }

    @Test
    void topWithoutNReturnsThreeEvents() throws Exception {
        when(ticketOffice.topEventsByBookedSeats(3)).thenReturn(List.of());

        mockMvc.perform(get("/events/top"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(ticketOffice).topEventsByBookedSeats(3);
    }

    @Test
    void topWithZeroNIsBadRequestProblemDetail() throws Exception {
        mockMvc.perform(get("/events/top").param("n", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(ticketOffice, never()).topEventsByBookedSeats(anyInt());
    }

    @Test
    void topReturnsEventsByBookedSeats() throws Exception {
        when(ticketOffice.topEventsByBookedSeats(2)).thenReturn(List.of(
                event("e2", "Festival", "Cluj", LocalDateTime.of(2030, 7, 3, 19, 0), 100, 80),
                event("e1", "Concert", "Cluj", LocalDateTime.of(2030, 6, 12, 20, 0), 100, 30)));

        mockMvc.perform(get("/events/top").param("n", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("e2"))
                .andExpect(jsonPath("$[0].availableSeats").value(20))
                .andExpect(jsonPath("$[1].id").value("e1"));
    }

    @Test
    void createsEventWithLocation() throws Exception {
        String location = mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Concert","venueName":"Arena","city":"Cluj",
                                 "startsAt":"2030-06-12T20:00:00","capacity":100}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Concert"))
                .andExpect(jsonPath("$.venueName").value("Arena"))
                .andExpect(jsonPath("$.city").value("Cluj"))
                .andExpect(jsonPath("$.startsAt").value("2030-06-12T20:00:00"))
                .andExpect(jsonPath("$.capacity").value(100))
                .andExpect(jsonPath("$.availableSeats").value(100))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        ArgumentCaptor<Event> added = ArgumentCaptor.forClass(Event.class);
        verify(ticketOffice).addEvent(added.capture());
        assertEquals("/events/" + added.getValue().getId(), location);
        assertEquals(0, added.getValue().getBookedSeats());
    }

    @Test
    void blankNameIsBadRequestProblemDetail() throws Exception {
        assertInvalidEvent("""
                {"name":"","venueName":"Arena","city":"Cluj","startsAt":"2030-06-12T20:00:00","capacity":100}""");
    }

    @Test
    void pastStartIsBadRequestProblemDetail() throws Exception {
        assertInvalidEvent("""
                {"name":"Concert","venueName":"Arena","city":"Cluj","startsAt":"2020-06-12T20:00:00","capacity":100}""");
    }

    @Test
    void zeroCapacityIsBadRequestProblemDetail() throws Exception {
        assertInvalidEvent("""
                {"name":"Concert","venueName":"Arena","city":"Cluj","startsAt":"2030-06-12T20:00:00","capacity":0}""");
    }

    private void assertInvalidEvent(String body) throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(ticketOffice, never()).addEvent(any());
    }
}
