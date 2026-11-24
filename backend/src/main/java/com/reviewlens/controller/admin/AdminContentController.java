package com.reviewlens.controller.admin;

import com.reviewlens.dto.AdminDtos.AdminDatasetDetail;
import com.reviewlens.dto.AdminDtos.AdminDatasetRow;
import com.reviewlens.dto.AdminDtos.AdminJobRow;
import com.reviewlens.dto.AdminDtos.AdminProductDetail;
import com.reviewlens.dto.AdminDtos.AdminProductRow;
import com.reviewlens.dto.AdminDtos.AdminReviewRow;
import com.reviewlens.dto.AdminDtos.BulkDeleteRequest;
import com.reviewlens.dto.AdminDtos.JobCounts;
import com.reviewlens.dto.AdminDtos.ReviewQualitySummary;
import com.reviewlens.dto.Dtos.JobDto;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.admin.AdminContentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {
    private final AdminContentService content;

    public AdminContentController(AdminContentService content) {
        this.content = content;
    }

    // ---------- datasets ----------
    @GetMapping("/datasets")
    public PageDto<AdminDatasetRow> datasets(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return content.datasets(q, status, from, to, page, size);
    }

    @GetMapping("/datasets/{id}")
    public AdminDatasetDetail dataset(@PathVariable String id) {
        return content.dataset(id);
    }

    @DeleteMapping("/datasets/{id}")
    public Map<String, Object> deleteDataset(@PathVariable String id, @RequestParam(required = false) String reason) {
        return content.deleteDataset(CurrentUser.get(), id, reason);
    }

    @PostMapping("/datasets/{id}/retry")
    public JobDto retryDataset(@PathVariable String id) {
        return content.retryDataset(CurrentUser.get(), id);
    }

    // ---------- products ----------
    @GetMapping("/products")
    public PageDto<AdminProductRow> products(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) String datasetId,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return content.products(q, datasetId, category, page, size);
    }

    @GetMapping("/products/{id}")
    public AdminProductDetail product(@PathVariable String id) {
        return content.product(id);
    }

    @DeleteMapping("/products/{id}")
    public Map<String, Object> deleteProduct(@PathVariable String id, @RequestParam(required = false) String reason) {
        return content.deleteProduct(CurrentUser.get(), id, reason);
    }

    // ---------- reviews ----------
    @GetMapping("/reviews")
    public PageDto<AdminReviewRow> reviews(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String datasetId,
                                           @RequestParam(required = false) String productId,
                                           @RequestParam(required = false) String flag,
                                           @RequestParam(required = false) String sentiment,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return content.reviews(q, datasetId, productId, flag, sentiment, page, size);
    }

    @GetMapping("/reviews/summary")
    public ReviewQualitySummary reviewSummary(@RequestParam(required = false) String datasetId) {
        return content.reviewSummary(datasetId);
    }

    @DeleteMapping("/reviews/{id}")
    public Map<String, Object> deleteReview(@PathVariable String id, @RequestParam(required = false) String reason) {
        return content.deleteReview(CurrentUser.get(), id, reason);
    }

    @PostMapping("/reviews/bulk-delete")
    public Map<String, Object> bulkDelete(@Valid @RequestBody BulkDeleteRequest req) {
        return content.bulkDeleteReviews(CurrentUser.get(), req);
    }

    // ---------- analysis jobs ----------
    @GetMapping("/analysis-jobs")
    public PageDto<AdminJobRow> jobs(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return content.jobs(status, q, page, size);
    }

    @GetMapping("/analysis-jobs/stats")
    public JobCounts jobStats() {
        return content.jobCounts();
    }

    @PostMapping("/analysis-jobs/{id}/retry")
    public JobDto retryJob(@PathVariable String id) {
        return content.retryJob(CurrentUser.get(), id);
    }
}
