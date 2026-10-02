package ro.fasttrackit.ticketing.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import ro.fasttrackit.ticketing.domain.BookingRequest;

public record BookingRequestBody(@NotBlank @Email String customerEmail, @Positive int seats) {

    public BookingRequest toDomain(String eventId) {
        return new BookingRequest(eventId, customerEmail, seats);
    }
}
