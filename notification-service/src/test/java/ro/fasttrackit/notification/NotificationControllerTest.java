package ro.fasttrackit.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web test ({@code @WebMvcTest}, the service mocked): {@code GET /notifications} returns the notifications as JSON.
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void listsTheNotifications() throws Exception {
        when(notificationService.latest()).thenReturn(List.of(new Notification(
                "b-1", "ana@mail.ro", "2 seat(s) for Rock Night on 12 Jun 2030 at 20:00 are confirmed.",
                LocalDateTime.of(2026, 10, 28, 18, 30))));

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value("b-1"))
                .andExpect(jsonPath("$[0].customerEmail").value("ana@mail.ro"))
                .andExpect(jsonPath("$[0].text").value("2 seat(s) for Rock Night on 12 Jun 2030 at 20:00 are confirmed."))
                .andExpect(jsonPath("$[0].notifiedAt").value("2026-10-28T18:30:00"));
    }
}
