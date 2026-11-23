# Benchmarks

All numbers below come from actual runs. Raw per-repetition samples are in `benchmark-results/raw/*.csv`.

## Method

- **Harness:** a custom harness, `backend/src/main/java/com/tripweaver/benchmark/BenchmarkHarness.java`. It runs warm-up executions and discards them, then times each repetition with `System.nanoTime()`. It reports nanoseconds per operation as median, p25, p75 and minimum. A sink value stops the JIT from removing the work being measured.
- **Input generation:** inputs are built outside the timed section, from fixed seeds (`SplittableRandom`).
- **Correctness check:** each implementation's results are compared with its baseline before timing starts. A mismatch aborts the run.
- **Scope:** algorithm time only. No database, network or rendering is included.
- **Command:**
  ```bash
  cd backend
  MAVEN_OPTS=-Xmx4g ./mvnw -q compile exec:java -Dexec.mainClass=com.tripweaver.benchmark.BenchmarkMain -Dexec.args="trie"
  ```

## Environment (run of 2026-11-23)

| Item | Value |
|---|---|
| CPU | AMD Ryzen 7 9800X3D 8-Core Processor (16 logical CPUs) |
| RAM | 31 GB |
| OS | Windows 11 Pro 10.0.26200 |
| JVM | OpenJDK 64-Bit Server VM, Temurin 25.0.1, `-Xmx4g` |
| Build | TripWeaver backend 0.0.1-SNAPSHOT, run through `mvn exec:java` |

Other processes were running on the machine during the run, so treat the numbers as indicative.

## Trie vs linear scan (autocomplete, limit 10)

- **Terms:** synthetic place-like names such as "Canlimgo Beach", including diacritics (é, ã, ç).
- **Queries:** 1,000 random prefixes of 2–5 characters taken from existing terms.
- **Baseline query count:** the linear scan uses 1,000 queries at N ≤ 10K and 50 queries at N ≥ 100K, because each query costs O(N).
- **Runs:** 5 warm-up and 15 measured repetitions per configuration.
- **Raw data:** `benchmark-results/raw/trie-20261123-141011.csv`

| Terms N | Trie median ns/query (p25–p75) | Linear scan median ns/query (p25–p75) | Speed-up |
|---|---|---|---|
| 1,000 | 1,852 (1,719–2,334) | 5,736 (5,216–5,858) | 3.1× |
| 10,000 | 2,752 (2,664–2,893) | 109,562 (107,278–114,272) | 40× |
| 100,000 | 1,770 (1,733–1,821) | 978,298 (946,150–993,185) | 553× |
| 1,000,000 | 1,523 (1,431–1,718) | 42,063,452 (41,943,785–42,389,661) | ~27,600× |

**Interpretation:**
- Trie query time stays roughly flat as N grows. This matches O(m log σ + visited subtree, stopped at 10 results).
- Linear-scan time grows with N, as expected from O(N·m) plus sorting the matches.
- The jump in linear-scan time between 100K and 1M is larger than 10×. Short prefixes match many more entries at 1M, so the baseline also pays to sort a larger hit list.
- Trie time does not rise with N because enumeration stops after 10 accepted results.

Phase 12 adds Quickselect, Set Cover, Heap and TSP.
