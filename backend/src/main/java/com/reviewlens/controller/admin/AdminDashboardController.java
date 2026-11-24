package com.reviewlens.controller.admin;

import com.reviewlens.dto.AdminDtos.AdminAnalytics;
import com.reviewlens.dto.AdminDtos.AdminDashboard;
import com.reviewlens.dto.AdminDtos.SystemHealth;
import com.reviewlens.exception.ApiException;
import com.reviewlens.security.AuthUser;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.HealthService;
import com.reviewlens.service.admin.AdminDashboardService;
import com.reviewlens.service.admin.AdminReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private static final Set<String> RANGES = Set.of("30d", "12w", "12m");

    private final AdminDashboardService dashboard;
    private final AdminReportService reports;
    private final HealthService health;

    public AdminDashboardController(AdminDashboardService dashboard, AdminReportService reports, HealthService health) {
        this.dashboard = dashboard;
        this.reports = reports;
        this.health = health;
    }

    @GetMapping("/dashboard")
    public AdminDashboard dashboard() {
        return dashboard.dashboard();
    }

    @GetMapping("/system/health")
    public SystemHealth health() {
        return health.check();
    }

    @GetMapping({"/analytics", "/reports"})
    public AdminAnalytics analytics(@RequestParam(defaultValue = "30d") String range) {
        return dashboard.analytics(range(range));
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportPdf(@RequestParam(defaultValue = "30d") String range,
                                            @RequestParam(defaultValue = "pdf") String format,
                                            HttpServletResponse response) throws IOException {
        AuthUser admin = CurrentUser.get();
        String r = range(range);
        String name = "reviewlens-platform-report-" + r + "-" + LocalDate.now();
        if ("csv".equalsIgnoreCase(format)) {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".csv\"");
            reports.exportCsv(admin, r, response.getOutputStream());
            return null;
        }
        if (!"pdf".equalsIgnoreCase(format)) throw ApiException.badRequest("format must be pdf or csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(reports.exportPdf(admin, r));
    }

    private static String range(String range) {
        if (!RANGES.contains(range)) throw ApiException.badRequest("range must be one of 30d, 12w, 12m");
        return range;
    }
}
