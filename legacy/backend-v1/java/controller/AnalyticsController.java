package com.reviewlens.controller;

import com.reviewlens.dto.Dtos.*;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.AnalyticsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final NlpClient nlp;

    public AnalyticsController(AnalyticsService analytics, NlpClient nlp) {
        this.analytics = analytics;
        this.nlp = nlp;
    }

    @GetMapping("/analytics/dashboard")
    public Dashboard dashboard(@RequestParam Long datasetId, @RequestParam(required = false) Long productId) {
        return analytics.dashboard(CurrentUser.id(), datasetId, productId);
    }

    @GetMapping("/analytics/compare")
    public Comparison compare(@RequestParam Long datasetId, @RequestParam List<Long> productIds) {
        return analytics.compare(CurrentUser.id(), datasetId, productIds);
    }

    /** Live single-review analysis (demo / "try it" page). */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(@Valid @RequestBody AnalyzeTextRequest req) {
        NlpClient.ReviewOut out = nlp.analyze(List.of(new NlpClient.ReviewIn(0L, req.text(), req.rating()))).get(0);
        List<AspectStat> stats = out.aspects().stream().map(a -> new AspectStat(a.aspect(), a.mentions(),
                a.sentiment().equals("POSITIVE") ? 1 : 0, a.sentiment().equals("NEGATIVE") ? 1 : 0,
                a.sentiment().equals("NEUTRAL") ? 1 : 0, a.score(),
                a.sentiment().equals("POSITIVE") ? 1 : a.sentiment().equals("NEGATIVE") ? -1 : 0)).toList();
        return Map.of("result", out, "insight", out.aspects().isEmpty()
                ? "No product aspects were detected in this review." : analytics.insight(stats, 1));
    }
}
