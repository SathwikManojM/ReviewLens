package com.reviewlens.controller;

import com.reviewlens.dto.Dtos.AuthResponse;
import com.reviewlens.dto.Dtos.LoginRequest;
import com.reviewlens.dto.Dtos.RegisterRequest;
import com.reviewlens.dto.Dtos.UserDto;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Module 1 - authentication. There is intentionally no endpoint to register an administrator. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req, false);
    }

    /** Separate administrator sign-in: succeeds only for active ADMIN accounts and is audited. */
    @PostMapping("/admin/login")
    public AuthResponse adminLogin(@Valid @RequestBody LoginRequest req) {
        return auth.login(req, true);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        auth.logout(CurrentUser.get());
    }

    @GetMapping("/me")
    public UserDto me() {
        return auth.me(CurrentUser.get());
    }
}
