package com.reviewlens.controller;

import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/{datasetId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long datasetId,
                                      @RequestParam(required = false) Long productId,
                                      @RequestParam(required = false) List<Long> compare) {
        byte[] pdf = reports.pdf(CurrentUser.id(), datasetId, productId, compare);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reviewlens-report-" + datasetId + "-" + LocalDate.now() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{datasetId}/csv")
    public void csv(@PathVariable Long datasetId, @RequestParam(required = false) Long productId,
                    HttpServletResponse response) throws IOException {
        Long userId = CurrentUser.id();
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"reviewlens-aspects-" + datasetId + "-" + LocalDate.now() + ".csv\"");
        reports.csv(userId, datasetId, productId, response.getOutputStream());
    }
}
