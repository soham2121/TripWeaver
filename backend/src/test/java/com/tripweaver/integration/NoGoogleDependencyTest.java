package com.tripweaver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 (decision D39): TripWeaver uses no Google API. The only Google artefact allowed is the plain
 * directions URL built in routing.NavigationLinks, which needs no key and returns nothing.
 */
class NoGoogleDependencyTest {

    /** Google API hosts, SDK packages and credential settings. */
    static final Pattern FORBIDDEN = Pattern.compile(
            "googleapis\\.com|maps\\.google\\.[a-z.]+/maps/api|com\\.google\\.maps|google-maps-services"
                    + "|places\\.google|GOOGLE_[A-Z_]*KEY|GOOGLE_PLACES|X-Goog-",
            Pattern.CASE_INSENSITIVE);

    static List<Path> files(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void mainSourcesCallNoGoogleApi() throws IOException {
        // Applied migrations are immutable history (V5 still names the removed provider in a CHECK);
        // the database itself is checked by scripts/verify-db.sql.
        List<String> hits = files(Path.of("src/main")).stream()
                .filter(p -> !p.toString().replace('\\', '/').contains("/db/"))
                .filter(p -> {
                    try {
                        return FORBIDDEN.matcher(Files.readString(p)).find();
                    } catch (IOException | java.io.UncheckedIOException e) {
                        return false; // binary resource
                    }
                })
                .map(Path::toString)
                .toList();
        assertEquals(List.of(), hits);
    }

    @Test
    void noGoogleClientDependency() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(!FORBIDDEN.matcher(pom).find() && !pom.contains("<groupId>com.google.maps"), "pom.xml");
    }

    @Test
    void onlyGoogleReferenceIsTheDirectionsUrl() throws IOException {
        List<String> users = files(Path.of("src/main/java")).stream()
                .filter(p -> {
                    try {
                        return Files.readString(p).contains("google.com/");
                    } catch (IOException e) {
                        return false;
                    }
                })
                .map(p -> p.getFileName().toString())
                .toList();
        assertEquals(List.of("NavigationLinks.java"), users);
    }
}
