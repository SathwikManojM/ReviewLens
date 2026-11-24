package com.reviewlens.controller.admin;

import com.reviewlens.dto.AdminDtos.AuditLogDto;
import com.reviewlens.dto.AdminDtos.SettingsDto;
import com.reviewlens.dto.AdminDtos.SettingsUpdate;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.AuditService;
import com.reviewlens.service.SettingsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Audit logs are read-only through the API; settings changes are validated and audited. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {
    private final AuditService audit;
    private final SettingsService settings;

    public AdminSystemController(AuditService audit, SettingsService settings) {
        this.audit = audit;
        this.settings = settings;
    }

    @GetMapping("/audit-logs")
    public PageDto<AuditLogDto> auditLogs(@RequestParam(required = false) String action,
                                          @RequestParam(required = false) String actor,
                                          @RequestParam(required = false) String resourceType,
                                          @RequestParam(required = false) String resourceId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        return audit.search(action, actor, resourceType, resourceId, from, to, page, size);
    }

    @GetMapping("/audit-logs/actions")
    public List<String> auditActions() {
        return audit.actions();
    }

    @GetMapping("/audit-logs/export")
    public void exportAudit(@RequestParam(required = false) String action,
                            @RequestParam(required = false) String actor,
                            @RequestParam(required = false) String resourceType,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            HttpServletResponse response) throws IOException {
        CurrentUser.get();
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reviewlens-audit-log-" + LocalDate.now() + ".csv\"");
        audit.exportCsv(action, actor, resourceType, from, to, response.getOutputStream());
    }

    @GetMapping("/settings")
    public SettingsDto getSettings() {
        return settings.dto();
    }

    @PutMapping("/settings")
    public SettingsDto updateSettings(@Valid @RequestBody SettingsUpdate req) {
        return settings.update(req, CurrentUser.get());
    }
}
