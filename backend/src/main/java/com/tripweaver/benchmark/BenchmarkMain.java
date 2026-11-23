package com.tripweaver.benchmark;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs benchmarks and writes raw CSV to {@code benchmark-results/raw/}.
 *
 * <pre>
 * cd backend
 * ./mvnw -q compile exec:java -Dexec.mainClass=com.tripweaver.benchmark.BenchmarkMain -Dexec.args="trie"
 * </pre>
 */
public final class BenchmarkMain {

    private BenchmarkMain() {}

    public static void main(String[] args) throws Exception {
        List<String> which = args.length == 0 ? List.of("trie") : List.of(args);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        System.out.println("environment: " + BenchmarkHarness.environment());
        for (String w : which) {
            List<BenchmarkHarness.Sample> samples = switch (w) {
                case "trie" -> TrieBenchmark.run(new int[] {1_000, 10_000, 100_000, 1_000_000}, 1_000, 5, 15);
                default -> throw new IllegalArgumentException("unknown benchmark " + w);
            };
            Path csv = Path.of("../benchmark-results/raw/" + w + "-" + stamp + ".csv");
            BenchmarkHarness.writeCsv(csv, samples);
            Map<String, List<BenchmarkHarness.Sample>> groups = new LinkedHashMap<>();
            for (var s : samples)
                groups.computeIfAbsent(s.variant() + "@" + s.size(), k -> new ArrayList<>())
                        .add(s);
            System.out.println("benchmark " + w + " -> " + csv);
            System.out.println("variant,size,reps,median_ns_per_op,p25,p75,min");
            for (var g : groups.values()) {
                var s = BenchmarkHarness.summarise(g);
                System.out.printf(
                        "%s,%d,%d,%.1f,%.1f,%.1f,%.1f%n",
                        s.variant(), s.size(), s.reps(), s.medianNsPerOp(), s.p25(), s.p75(), s.min());
            }
        }
        System.out.println("sink=" + BenchmarkHarness.sink());
    }
}
