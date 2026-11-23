package com.tripweaver.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Assessed algorithms must be plain Java: no Spring, persistence, HTTP or JDK PriorityQueue. */
class AlgorithmIsolationTest {

    @Test
    void algorithmSourcesHaveNoFrameworkDependencies() throws IOException {
        Path root = Path.of("src/main/java/com/tripweaver/algorithms");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(files.size() >= 15, "expected algorithm sources, found " + files.size());
        List<String> forbidden =
                List.of("org.springframework", "jakarta.", "javax.persistence", "java.net.http", "PriorityQueue");
        List<String> offenders = files.stream()
                .filter(p -> {
                    try {
                        String src = Files.readString(p);
                        return forbidden.stream().anyMatch(src::contains);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Path::toString)
                .toList();
        assertEquals(List.of(), offenders);
    }
}
