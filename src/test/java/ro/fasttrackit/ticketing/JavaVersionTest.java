package ro.fasttrackit.ticketing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test (no Spring): checks that the build runs on the Java 25 toolchain.
 */
class JavaVersionTest {

    @Test
    void runsOnJava25() {
        assertEquals(25, Runtime.version().feature());
    }
}
