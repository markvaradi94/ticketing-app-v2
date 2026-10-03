package ro.fasttrackit.ticketing.domain;

public record Venue(String name, String city) {

    public Venue {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Venue name must not be blank");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("Venue city must not be blank");
        }
    }
}
