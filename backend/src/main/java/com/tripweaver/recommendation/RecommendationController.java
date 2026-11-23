package com.tripweaver.recommendation;

import com.tripweaver.common.AlgorithmRunLog;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final RecommendationService service;
    private final AlgorithmRunLog runs;

    public RecommendationController(RecommendationService service, AlgorithmRunLog runs) {
        this.service = service;
        this.runs = runs;
    }

    /** Candidate selection only (no scheduling). Used by the algorithm inspector and the planner. */
    @PostMapping("/recommendations/preview")
    public RecommendationService.Response preview(@RequestBody RecommendationRequest req) {
        return service.recommend(req, null, "preview");
    }

    public record AlgorithmInfo(String name, String unit, String role, String complexity, String guarantee) {}

    static final List<AlgorithmInfo> ALGORITHMS = List.of(
            new AlgorithmInfo(
                    "Trie", "Unit 4", "Place/category/area autocomplete", "O(m log σ + results)", "Exact prefix match"),
            new AlgorithmInfo(
                    "Randomized Quickselect",
                    "Unit 1 / Unit 6 (CO5)",
                    "Top-K candidate pruning with per-interest quotas",
                    "Expected O(n), worst O(n²)",
                    "Exact top-K under total order; seed affects work only"),
            new AlgorithmInfo(
                    "Greedy Weighted Set Cover",
                    "Unit 5",
                    "Diverse experience selection",
                    "O(r · Σ|S|)",
                    "H(d)·OPT for equal weights on the cover objective only"),
            new AlgorithmInfo(
                    "Indexed Binary Max Heap",
                    "Unit 2",
                    "Lazy greedy priorities",
                    "O(log n) update/extract, O(n) build",
                    "Same picks as full greedy"),
            new AlgorithmInfo(
                    "Metric TSP Double-Tree",
                    "Unit 5",
                    "Daily stop ordering",
                    "O(n²)",
                    "≤ 2·OPT only on validated metric matrices"));

    @GetMapping("/algorithms/metadata")
    public Map<String, Object> metadata(@RequestParam(defaultValue = "20") int limit) {
        return Map.of(
                "version",
                AlgorithmRunLog.VERSION,
                "algorithms",
                ALGORITHMS,
                "recentRuns",
                runs.recent(Math.max(1, Math.min(limit, 100))));
    }
}
