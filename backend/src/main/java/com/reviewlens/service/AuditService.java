package com.reviewlens.service;

import com.reviewlens.dto.AdminDtos.AuditLogDto;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.model.AuditLog;
import com.reviewlens.security.AuthUser;
import com.reviewlens.support.MongoSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Append-only audit trail. This service deliberately offers no update or delete operation, and metadata keys
 * that look like secrets are stripped before writing.
 */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Set<String> SENSITIVE = Set.of("password", "token", "secret", "hash", "authorization", "credential");

    private final MongoTemplate mongo;

    public AuditService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void record(AuthUser actor, String action, String resourceType, String resourceId, String label,
                       Map<String, Object> metadata) {
        insert(actor == null ? null : actor.id(), actor == null ? null : actor.email(), action, resourceType, resourceId, label, metadata);
    }

    /** For events without an authenticated actor (e.g. a failed administrator sign-in). */
    public void recordAnonymous(String email, String action, String resourceType, String resourceId, String label,
                                Map<String, Object> metadata) {
        insert(null, email, action, resourceType, resourceId, label, metadata);
    }

    public void recordSystem(String action, String resourceType, String resourceId, String label, Map<String, Object> metadata) {
        insert(null, "system", action, resourceType, resourceId, label, metadata);
    }

    private void insert(String actorId, String actorEmail, String action, String resourceType, String resourceId,
                        String label, Map<String, Object> metadata) {
        try {
            mongo.insert(new AuditLog(null, actorId, actorEmail, action, resourceType, resourceId,
                    MongoSupport.trunc(label, 300), sanitize(metadata), clientIp(), Instant.now()));
        } catch (RuntimeException e) {
            log.error("Could not write audit log entry {}: {}", action, e.getMessage());
        }
    }

    public PageDto<AuditLogDto> search(String action, String actor, String resourceType, String resourceId,
                                       LocalDate from, LocalDate to, int page, int size) {
        Supplier<Query> q = () -> query(action, actor, resourceType, resourceId, from, to);
        int s = Math.min(Math.max(size, 1), 200), p = Math.max(page, 0);
        long total = mongo.count(q.get(), AuditLog.class);
        List<AuditLog> logs = mongo.find(q.get().with(Sort.by(Sort.Direction.DESC, "timestamp")).skip((long) p * s).limit(s), AuditLog.class);
        return PageDto.of(logs.stream().map(AuditService::toDto).toList(), p, s, total);
    }

    public List<String> actions() {
        return mongo.findDistinct(new Query(), "action", AuditLog.class, String.class).stream().sorted().toList();
    }

    public List<AuditLogDto> recentForUser(String userId, int limit) {
        Query q = new Query(new Criteria().orOperator(where("actorId").is(userId), where("resourceId").is(userId)))
                .with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(limit);
        return mongo.find(q, AuditLog.class).stream().map(AuditService::toDto).toList();
    }

    public List<AuditLog> latest(int limit) {
        return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(limit), AuditLog.class);
    }

    public void exportCsv(String action, String actor, String resourceType, LocalDate from, LocalDate to, OutputStream os) throws IOException {
        Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        w.write('﻿');
        try (CSVPrinter p = new CSVPrinter(w, CSVFormat.DEFAULT.builder().setHeader("timestamp", "action", "actor",
                "resource_type", "resource_id", "resource", "ip_address", "metadata").get())) {
            Query q = query(action, actor, resourceType, null, from, to).with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(50_000);
            for (AuditLog l : mongo.find(q, AuditLog.class)) {
                p.printRecord(l.timestamp(), l.action(), l.actorEmail(), l.resourceType(), l.resourceId(), l.resourceLabel(),
                        l.ipAddress(), l.metadata() == null ? "" : l.metadata().toString());
            }
        }
    }

    private Query query(String action, String actor, String resourceType, String resourceId, LocalDate from, LocalDate to) {
        List<Criteria> parts = new ArrayList<>();
        if (notBlank(action)) parts.add(where("action").is(action));
        if (notBlank(actor)) parts.add(where("actorEmail").regex(MongoSupport.escapeRegex(actor.trim()), "i"));
        if (notBlank(resourceType)) parts.add(where("resourceType").is(resourceType));
        if (notBlank(resourceId)) parts.add(where("resourceId").is(resourceId));
        if (from != null || to != null) {
            Criteria c = where("timestamp");
            if (from != null) c = c.gte(MongoSupport.startOfDay(from));
            if (to != null) c = c.lt(MongoSupport.startOfDay(to.plusDays(1)));
            parts.add(c);
        }
        return parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
    }

    public static AuditLogDto toDto(AuditLog l) {
        return new AuditLogDto(l.id(), l.actorId(), l.actorEmail(), l.action(), l.resourceType(), l.resourceId(),
                l.resourceLabel(), l.metadata(), l.ipAddress(), l.timestamp());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitize(Map<String, Object> metadata) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metadata == null) return out;
        metadata.forEach((k, v) -> {
            String key = k.replace('.', '_').replace('$', '_');
            String lower = key.toLowerCase(Locale.ROOT);
            if (SENSITIVE.stream().anyMatch(lower::contains)) return;
            out.put(key, v instanceof Map<?, ?> m ? sanitize((Map<String, Object>) m) : v);
        });
        return out;
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            return req.getRemoteAddr();
        }
        return null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
