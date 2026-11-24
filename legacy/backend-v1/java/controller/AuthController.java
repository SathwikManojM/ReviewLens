package com.reviewlens.controller;

import com.reviewlens.dto.Dtos.*;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.security.CurrentUser;
import com.reviewlens.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    private final NlpClient nlp;

    public AuthController(AuthService auth, NlpClient nlp) {
        this.auth = auth;
        this.nlp = nlp;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @GetMapping("/auth/me")
    public UserDto me() {
        return auth.me(CurrentUser.id());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("backend", "UP", "nlpEngine", nlp.isUp() ? "UP" : "DOWN");
    }
}
