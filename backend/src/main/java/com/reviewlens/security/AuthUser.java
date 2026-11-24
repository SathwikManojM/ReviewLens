package com.reviewlens.security;

import com.reviewlens.model.Role;

/** The authenticated principal, rebuilt from the database on every request. */
public record AuthUser(String id, String name, String email, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
