package com.reviewlens.controller;

import com.reviewlens.dto.Dtos.*;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.DatasetService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
    private final DatasetService service;

    public DatasetController(DatasetService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DatasetDto upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "name", required = false) String name) {
        return service.upload(CurrentUser.id(), file, name);
    }

    @GetMapping
    public List<DatasetDto> list() {
        return service.list(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public DatasetDto get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/reprocess")
    public DatasetDto reprocess(@PathVariable Long id) {
        return service.reprocess(CurrentUser.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/products")
    public List<ProductDto> products(@PathVariable Long id) {
        return service.products(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/reviews")
    public PageDto<ReviewDto> reviews(@PathVariable Long id,
                                      @RequestParam(required = false) Long productId,
                                      @RequestParam(required = false) String aspect,
                                      @RequestParam(required = false) String sentiment,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.reviews(CurrentUser.id(), id, productId, aspect, sentiment, q, page, size);
    }
}
