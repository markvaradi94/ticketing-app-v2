package ro.fasttrackit.ticketing.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewRequestBody(@NotBlank String author, @Min(1) @Max(5) int rating, String comment) {
}
