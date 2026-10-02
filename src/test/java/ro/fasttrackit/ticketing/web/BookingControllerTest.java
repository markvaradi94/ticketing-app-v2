package ro.fasttrackit.ticketing.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.TicketOffice;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
 * Controller test ({@code @WebMvcTest}, no Spring context beyond MVC): checks {@link BookingController}
 * against a mocked {@link TicketOffice}.
 */
@WebMvcTest(BookingController.class)
class BookingControllerTest {

    private static final String VALID_BODY = """
            {"customerEmail":"ana@mail.ro","seats":2}""";

    private static final BookingRequest VALID_REQUEST = new BookingRequest("e1", "ana@mail.ro", 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketOffice ticketOffice;

    private static Booking booking(String id, String eventId) {
        return Booking.builder()
                .id(id)
                .eventId(eventId)
                .customerEmail("ana@mail.ro")
                .seats(2)
                .bookedAt(LocalDateTime.of(2030, 1, 5, 10, 30))
                .build();
    }

    @Test
    void confirmedBookingIsCreated() throws Exception {
        when(ticketOffice.book(VALID_REQUEST)).thenReturn(new BookingResult.Confirmed(booking("b1", "e1")));

        mockMvc.perform(post("/events/e1/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value("b1"))
                .andExpect(jsonPath("$.eventId").value("e1"))
                .andExpect(jsonPath("$.customerEmail").value("ana@mail.ro"))
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.bookedAt").value("2030-01-05T10:30:00"));
    }

    @Test
    void unknownEventIsNotFoundProblemDetail() throws Exception {
        when(ticketOffice.book(new BookingRequest("e9", "ana@mail.ro", 2)))
                .thenReturn(new BookingResult.UnknownEvent("e9"));

        mockMvc.perform(post("/events/e9/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("e9")));
    }

    @Test
    void soldOutIsConflictWithAvailableSeats() throws Exception {
        when(ticketOffice.book(VALID_REQUEST)).thenReturn(new BookingResult.SoldOut("e1", 1));

        mockMvc.perform(post("/events/e1/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.availableSeats").value(1));
    }

    @Test
    void alreadyStartedIsConflictWithStartsAt() throws Exception {
        when(ticketOffice.book(VALID_REQUEST))
                .thenReturn(new BookingResult.AlreadyStarted("e1", LocalDateTime.of(2026, 3, 1, 20, 0)));

        mockMvc.perform(post("/events/e1/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.startsAt").value("2026-03-01T20:00:00"));
    }

    @Test
    void blankEmailIsBadRequestProblemDetail() throws Exception {
        assertInvalidBooking("""
                {"customerEmail":"","seats":2}""");
    }

    @Test
    void zeroSeatsIsBadRequestProblemDetail() throws Exception {
        assertInvalidBooking("""
                {"customerEmail":"ana@mail.ro","seats":0}""");
    }

    @Test
    void returnsBookingById() throws Exception {
        when(ticketOffice.findBooking("b1")).thenReturn(Optional.of(booking("b1", "e1")));

        mockMvc.perform(get("/events/e1/bookings/b1"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value("b1"))
                .andExpect(jsonPath("$.eventId").value("e1"))
                .andExpect(jsonPath("$.customerEmail").value("ana@mail.ro"))
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.bookedAt").value("2030-01-05T10:30:00"));
    }

    @Test
    void unknownBookingIsNotFoundProblemDetail() throws Exception {
        when(ticketOffice.findBooking("b9")).thenReturn(Optional.empty());

        mockMvc.perform(get("/events/e1/bookings/b9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("b9")));
    }

    @Test
    void bookingOfAnotherEventIsNotFoundProblemDetail() throws Exception {
        when(ticketOffice.findBooking("b1")).thenReturn(Optional.of(booking("b1", "e2")));

        mockMvc.perform(get("/events/e1/bookings/b1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    private void assertInvalidBooking(String body) throws Exception {
        mockMvc.perform(post("/events/e1/bookings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(ticketOffice, never()).book(any());
    }
}
