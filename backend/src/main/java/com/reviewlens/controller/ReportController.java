package com.reviewlens.controller;

import com.reviewlens.dto.Dtos.CreateReportRequest;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.dto.Dtos.ReportDto;
import com.reviewlens.model.Report;
import com.reviewlens.security.AuthUser;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    // ---------- report library ----------
    @GetMapping
    public PageDto<ReportDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reports.list(CurrentUser.get(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReportDto create(@Valid @RequestBody CreateReportRequest req) {
        return reports.create(CurrentUser.get(), req);
    }

    @GetMapping("/{id}")
    public ReportDto get(@PathVariable String id) {
        return reports.get(CurrentUser.get(), id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        Report r = reports.downloadable(CurrentUser.get(), id);
        MediaType type = r.getFormat() == Report.Format.PDF ? MediaType.APPLICATION_PDF : new MediaType("text", "csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(r.getFileName()).build().toString())
                .contentType(type)
                .body(new FileSystemResource(Path.of(r.getStoredPath())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        reports.delete(CurrentUser.get(), id);
    }

    // ---------- direct downloads (v1 API, kept for compatibility) ----------
    @GetMapping("/{datasetId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String datasetId,
                                      @RequestParam(required = false) String productId,
                                      @RequestParam(required = false) List<String> compare) {
        byte[] pdf = reports.pdf(CurrentUser.get(), datasetId, productId, compare);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reviewlens-report-" + LocalDate.now() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{datasetId}/csv")
    public void csv(@PathVariable String datasetId, @RequestParam(required = false) String productId,
                    HttpServletResponse response) throws IOException {
        AuthUser user = CurrentUser.get();
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reviewlens-aspects-" + LocalDate.now() + ".csv\"");
        reports.csv(user, datasetId, productId, response.getOutputStream());
    }
}
