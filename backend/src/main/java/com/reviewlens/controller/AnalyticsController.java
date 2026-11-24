package com.reviewlens.controller;

import com.reviewlens.dto.Dtos.AnalyzeResult;
import com.reviewlens.dto.Dtos.AnalyzeTextRequest;
import com.reviewlens.dto.Dtos.AspectDetail;
import com.reviewlens.dto.Dtos.AspectOverview;
import com.reviewlens.dto.Dtos.Comparison;
import com.reviewlens.dto.Dtos.Dashboard;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.AnalyticsService;
import com.reviewlens.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final SettingsService settings;

    public AnalyticsController(AnalyticsService analytics, SettingsService settings) {
        this.analytics = analytics;
        this.settings = settings;
    }

    @GetMapping("/analytics/dashboard")
    public Dashboard dashboard(@RequestParam String datasetId, @RequestParam(required = false) String productId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.dashboard(CurrentUser.get(), datasetId, productId, from, to);
    }

    @GetMapping("/analytics/compare")
    public Comparison compare(@RequestParam String datasetId, @RequestParam List<String> productIds,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.compare(CurrentUser.get(), datasetId, productIds, from, to);
    }

    @GetMapping("/analytics/aspects")
    public AspectOverview aspects(@RequestParam String datasetId, @RequestParam(required = false) String productId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.aspects(CurrentUser.get(), datasetId, productId, from, to);
    }

    @GetMapping("/analytics/aspects/detail")
    public AspectDetail aspectDetail(@RequestParam String datasetId, @RequestParam String aspect,
                                     @RequestParam(required = false) String productId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.aspectDetail(CurrentUser.get(), datasetId, aspect, productId, from, to);
    }

    /** Live single-review analysis (Live analyzer page). */
    @PostMapping("/analyze")
    public AnalyzeResult analyze(@Valid @RequestBody AnalyzeTextRequest req) {
        CurrentUser.get();
        settings.requireFeature("liveAnalyzer", "The live analyzer");
        return analytics.analyzeText(req.text(), req.rating());
    }
}
