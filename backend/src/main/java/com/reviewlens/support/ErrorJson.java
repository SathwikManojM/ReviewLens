package com.reviewlens.support;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;

/** Writes the API's JSON error shape from servlet filters (outside Spring MVC). */
public final class ErrorJson {
    private ErrorJson() {}

    public static void write(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"code\":\"" + esc(code) + "\",\"message\":\"" + esc(message)
                + "\",\"timestamp\":\"" + Instant.now() + "\"}");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\r\\n\\t]", " ");
    }
}
