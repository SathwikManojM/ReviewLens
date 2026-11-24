package com.reviewlens.controller.admin;

import com.reviewlens.dto.AdminDtos.AdminUserDetail;
import com.reviewlens.dto.AdminDtos.AdminUserRow;
import com.reviewlens.dto.AdminDtos.RoleUpdate;
import com.reviewlens.dto.AdminDtos.StatusUpdate;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.admin.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    public PageDto<AdminUserRow> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String role,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String sort,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return users.list(q, role, status, sort, page, size);
    }

    @GetMapping("/{id}")
    public AdminUserDetail detail(@PathVariable String id) {
        return users.detail(id);
    }

    @PatchMapping("/{id}/status")
    public AdminUserRow status(@PathVariable String id, @Valid @RequestBody StatusUpdate req) {
        return users.setStatus(CurrentUser.get(), id, req);
    }

    @PatchMapping("/{id}/role")
    public AdminUserRow role(@PathVariable String id, @Valid @RequestBody RoleUpdate req) {
        return users.setRole(CurrentUser.get(), id, req);
    }
}
