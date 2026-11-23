package com.tripweaver.benchmark;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Minimal reproducible micro-benchmark harness (documented custom harness, plan section 13.8).
 *
 * <p>Input generation happens outside {@link #measure}. Each run returns an operation count; time per
 * operation is reported. JIT warm-up runs are discarded. A blackhole sink prevents dead-code elimination.
 */
public final class BenchmarkHarness {

    public record Sample(String algorithm, String variant, String sizeLabel, long size, int rep, long nanos, long ops) {
        double nanosPerOp() {
            return ops == 0 ? nanos : (double) nanos / ops;
        }
    }

    public record Summary(
            String algorithm,
            String variant,
            long size,
            int reps,
            double medianNsPerOp,
            double p25,
            double p75,
            double min) {}

    private static long sink;

    private BenchmarkHarness() {}

    /** Runs {@code warmup} discarded and {@code reps} measured executions of {@code run}. */
    public static List<Sample> measure(
            String algorithm, String variant, long size, int warmup, int reps, LongSupplier run) {
        for (int i = 0; i < warmup; i++) sink += run.getAsLong();
        List<Sample> out = new ArrayList<>(reps);
        for (int r = 0; r < reps; r++) {
            long t0 = System.nanoTime();
            long ops = run.getAsLong();
            long dt = System.nanoTime() - t0;
            sink += ops;
            out.add(new Sample(algorithm, variant, String.valueOf(size), size, r, dt, ops));
        }
        return out;
    }

    public static Summary summarise(List<Sample> samples) {
        double[] v = samples.stream().mapToDouble(Sample::nanosPerOp).sorted().toArray();
        Sample s = samples.getFirst();
        return new Summary(
                s.algorithm(),
                s.variant(),
                s.size(),
                v.length,
                quantile(v, 0.5),
                quantile(v, 0.25),
                quantile(v, 0.75),
                v[0]);
    }

    static double quantile(double[] sorted, double q) {
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }

    public static void writeCsv(Path file, List<Sample> samples) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println("algorithm,variant,size,rep,nanos,ops,nanos_per_op");
            for (Sample s : samples)
                w.printf(
                        "%s,%s,%d,%d,%d,%d,%.3f%n",
                        s.algorithm(), s.variant(), s.size(), s.rep(), s.nanos(), s.ops(), s.nanosPerOp());
        }
    }

    public static String environment() {
        Runtime rt = Runtime.getRuntime();
        return String.join(
                "; ",
                "java=" + System.getProperty("java.version"),
                "vm=" + System.getProperty("java.vm.name"),
                "os=" + System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "arch=" + System.getProperty("os.arch"),
                "cpus=" + rt.availableProcessors(),
                "maxHeapMB=" + rt.maxMemory() / (1024 * 1024),
                "jvmArgs="
                        + Arrays.toString(java.lang.management.ManagementFactory.getRuntimeMXBean()
                                .getInputArguments()
                                .toArray()));
    }

    /** Keeps the JIT from discarding results. */
    public static long sink() {
        return sink;
    }
}
