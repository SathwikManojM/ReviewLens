package com.reviewlens.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal HS256 JSON Web Token implementation (no external dependency). */
@Service
public class JwtService {
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern SUB = Pattern.compile("\"sub\":\"(\\d+)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(@Value("${reviewlens.jwt.secret}") String secret,
                      @Value("${reviewlens.jwt.expiration-hours:24}") long hours) {
        if (secret.length() < 32) throw new IllegalStateException("reviewlens.jwt.secret must be at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = hours * 3600;
    }

    public String generate(Long userId) {
        long now = Instant.now().getEpochSecond();
        String payload = "{\"sub\":\"" + userId + "\",\"iat\":" + now + ",\"exp\":" + (now + expirationSeconds) + "}";
        String unsigned = HEADER + "." + ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + ENC.encodeToString(sign(unsigned));
    }

    /** Returns the user id if the token is authentic and not expired. */
    public Optional<Long> validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, DEC.decode(parts[2]))) return Optional.empty();
            String payload = new String(DEC.decode(parts[1]), StandardCharsets.UTF_8);
            Matcher exp = EXP.matcher(payload), sub = SUB.matcher(payload);
            if (!exp.find() || !sub.find()) return Optional.empty();
            if (Long.parseLong(exp.group(1)) < Instant.now().getEpochSecond()) return Optional.empty();
            return Optional.of(Long.parseLong(sub.group(1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign JWT", e);
        }
    }
}
