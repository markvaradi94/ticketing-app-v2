package ro.fasttrackit.ticketing.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import ro.fasttrackit.ticketing.domain.Booking;

import java.time.LocalDateTime;

@Document("bookings")
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")
public class BookingDocument {
    @Id
    private String id;
    private String eventId;
    private String customerEmail;
    private int seats;
    private LocalDateTime bookedAt;

    public static BookingDocument from(Booking booking) {
        return BookingDocument.builder()
                .id(booking.getId())
                .eventId(booking.getEventId())
                .customerEmail(booking.getCustomerEmail())
                .seats(booking.getSeats())
                .bookedAt(booking.getBookedAt())
                .build();
    }

    public Booking toDomain() {
        return Booking.builder()
                .id(id)
                .eventId(eventId)
                .customerEmail(customerEmail)
                .seats(seats)
                .bookedAt(bookedAt)
                .build();
    }
}
