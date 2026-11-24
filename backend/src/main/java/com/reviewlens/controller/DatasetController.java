package com.reviewlens.controller;

import com.reviewlens.dto.Dtos.DatasetDto;
import com.reviewlens.dto.Dtos.JobDto;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.dto.Dtos.PreviewDto;
import com.reviewlens.dto.Dtos.ProductDto;
import com.reviewlens.dto.Dtos.ReviewDto;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.DatasetService;
import com.reviewlens.service.ReviewSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
    private final DatasetService service;

    public DatasetController(DatasetService service) {
        this.service = service;
    }

    /** multipart: file (required), name (optional), mapping (optional JSON part {field: column}). */
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetDto upload(@RequestPart("file") MultipartFile file,
                             @RequestParam(value = "name", required = false) String name,
                             @RequestPart(value = "mapping", required = false) Map<String, String> mapping) {
        return service.upload(CurrentUser.get(), file, name, mapping);
    }

    /** The client sends the first ~64 KB of the file; returns columns, detected mapping and sample rows. */
    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    public PreviewDto preview(@RequestPart("file") MultipartFile sample) {
        CurrentUser.get();
        return service.preview(sample);
    }

    @GetMapping
    public List<DatasetDto> list() {
        return service.list(CurrentUser.get());
    }

    @GetMapping("/{id}")
    public DatasetDto get(@PathVariable String id) {
        return service.get(CurrentUser.get(), id);
    }

    @GetMapping("/{id}/jobs")
    public List<JobDto> jobs(@PathVariable String id) {
        return service.jobs(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/reprocess")
    public DatasetDto reprocess(@PathVariable String id) {
        return service.reprocess(CurrentUser.get(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(CurrentUser.get(), id);
    }

    @GetMapping("/{id}/products")
    public List<ProductDto> products(@PathVariable String id) {
        return service.products(CurrentUser.get(), id);
    }

    @GetMapping("/{id}/reviews")
    public PageDto<ReviewDto> reviews(@PathVariable String id,
                                      @RequestParam(required = false) String productId,
                                      @RequestParam(required = false) String aspect,
                                      @RequestParam(required = false) String sentiment,
                                      @RequestParam(required = false) Integer rating,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String sort,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.reviews(CurrentUser.get(), id,
                new ReviewSearchService.Filter(id, productId, aspect, sentiment, rating, q, null, sort, page, size));
    }
}
