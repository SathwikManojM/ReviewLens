package com.reviewlens.security;

import com.reviewlens.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HS256 JSON Web Tokens. Claims: sub (user id), tv (token version), iat, exp.
 * The token carries no role - authorisation always uses the role stored in MongoDB.
 */
@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern SUB = Pattern.compile("\"sub\":\"([0-9a-f]{24})\"");
    private static final Pattern TV = Pattern.compile("\"tv\":(\\d+)");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");

    public record Issued(String token, Instant expiresAt) {}
    public record Claims(String userId, int tokenVersion) {}

    private final byte[] secret;

    public JwtService(@Value("${reviewlens.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("JWT_SECRET is not set - using a random per-process secret; all sessions end when the server restarts. "
                    + "Set JWT_SECRET (at least 32 characters) in .env for persistent sessions.");
        } else if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        } else {
            this.secret = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public Issued generate(User user, long hours) {
        long now = Instant.now().getEpochSecond();
        long exp = now + hours * 3600;
        String payload = "{\"sub\":\"" + user.getId() + "\",\"tv\":" + user.getTokenVersion()
                + ",\"iat\":" + now + ",\"exp\":" + exp + "}";
        String unsigned = HEADER + "." + ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new Issued(unsigned + "." + ENC.encodeToString(sign(unsigned)), Instant.ofEpochSecond(exp));
    }

    /** Returns the claims if the signature is valid and the token has not expired. */
    public Optional<Claims> validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !HEADER.equals(parts[0])) return Optional.empty();
            if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), DEC.decode(parts[2]))) return Optional.empty();
            String payload = new String(DEC.decode(parts[1]), StandardCharsets.UTF_8);
            Matcher sub = SUB.matcher(payload), tv = TV.matcher(payload), exp = EXP.matcher(payload);
            if (!sub.find() || !tv.find() || !exp.find()) return Optional.empty();
            if (Long.parseLong(exp.group(1)) < Instant.now().getEpochSecond()) return Optional.empty();
            return Optional.of(new Claims(sub.group(1), Integer.parseInt(tv.group(1))));
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
