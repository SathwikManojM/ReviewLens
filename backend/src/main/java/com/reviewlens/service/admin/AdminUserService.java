package com.reviewlens.service.admin;

import com.reviewlens.dto.AdminDtos.AdminUserDetail;
import com.reviewlens.dto.AdminDtos.AdminUserRow;
import com.reviewlens.dto.AdminDtos.RoleUpdate;
import com.reviewlens.dto.AdminDtos.StatusUpdate;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.AnalysisJob;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.Role;
import com.reviewlens.model.User;
import com.reviewlens.repository.AnalysisJobRepository;
import com.reviewlens.repository.ReportRepository;
import com.reviewlens.repository.UserRepository;
import com.reviewlens.security.AuthUser;
import com.reviewlens.service.AuditService;
import com.reviewlens.service.DatasetService;
import com.reviewlens.support.MongoSupport;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Admin user management. Role and status changes are serialised and protected against removing the last admin. */
@Service
public class AdminUserService {
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final AnalysisJobRepository jobs;
    private final ReportRepository reports;
    private final AuditService audit;

    public AdminUserService(MongoTemplate mongo, UserRepository users, AnalysisJobRepository jobs,
                            ReportRepository reports, AuditService audit) {
        this.mongo = mongo;
        this.users = users;
        this.jobs = jobs;
        this.reports = reports;
        this.audit = audit;
    }

    public PageDto<AdminUserRow> list(String q, String role, String status, String sort, int page, int size) {
        List<Criteria> parts = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String rx = MongoSupport.escapeRegex(q.trim());
            parts.add(new Criteria().orOperator(where("name").regex(rx, "i"), where("email").regex(rx, "i")));
        }
        if (role != null && !role.isBlank()) parts.add(where("role").is(parseRole(role)));
        if ("active".equalsIgnoreCase(status)) parts.add(where("active").is(true));
        else if ("inactive".equalsIgnoreCase(status)) parts.add(where("active").is(false));
        Supplier<Query> qs = () -> parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
        int s = Math.min(Math.max(size, 1), 100), p = Math.max(page, 0);
        long total = mongo.count(qs.get(), User.class);
        Sort order = switch (sort == null ? "" : sort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "lastLogin" -> Sort.by(Sort.Direction.DESC, "lastLoginAt");
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        List<User> list = mongo.find(qs.get().with(order).skip((long) p * s).limit(s), User.class);
        Map<String, long[]> usage = usage(list.stream().map(User::getId).toList());
        return PageDto.of(list.stream().map(u -> row(u, usage.get(u.getId()))).toList(), p, s, total);
    }

    public AdminUserDetail detail(String id) {
        User u = find(id);
        long[] us = usage(List.of(id)).get(id);
        List<Dataset> recent = mongo.find(query(where("ownerId").is(id)).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(6), Dataset.class);
        return new AdminUserDetail(row(u, us), reports.countByOwnerId(id), jobs.countByOwnerId(id),
                jobs.countByOwnerIdAndStatus(id, AnalysisJob.Status.FAILED),
                recent.stream().map(d -> DatasetService.toDto(d, null)).toList(), audit.recentForUser(id, 15));
    }

    public synchronized AdminUserRow setStatus(AuthUser admin, String id, StatusUpdate req) {
        User target = find(id);
        boolean active = req.active();
        if (target.isActive() == active) return row(target, usage(List.of(id)).get(id));
        if (!active && target.getId().equals(admin.id())) {
            throw ApiException.badRequest("You cannot deactivate your own account");
        }
        if (!active && target.getRole() == Role.ADMIN && activeAdmins() <= 1) {
            throw ApiException.conflict("LAST_ADMIN", "The last active administrator cannot be deactivated");
        }
        Update update = new Update().set("active", active).set("updatedAt", Instant.now());
        if (!active) update.inc("tokenVersion", 1);           // ends the user's sessions immediately
        mongo.updateFirst(query(where("_id").is(id)), update, User.class);
        if (activeAdmins() == 0) {                            // safety net against concurrent changes
            mongo.updateFirst(query(where("_id").is(id)), new Update().set("active", true), User.class);
            throw ApiException.conflict("LAST_ADMIN", "The last active administrator cannot be deactivated");
        }
        audit.record(admin, active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", id, target.getEmail(), reason(req.reason()));
        return row(find(id), usage(List.of(id)).get(id));
    }

    public synchronized AdminUserRow setRole(AuthUser admin, String id, RoleUpdate req) {
        User target = find(id);
        Role role = parseRole(req.role());
        if (target.getRole() == role) return row(target, usage(List.of(id)).get(id));
        if (target.getRole() == Role.ADMIN && role == Role.USER && target.isActive() && activeAdmins() <= 1) {
            throw ApiException.conflict("LAST_ADMIN", "The last active administrator cannot be demoted. Promote another administrator first.");
        }
        mongo.updateFirst(query(where("_id").is(id)), new Update().set("role", role).inc("tokenVersion", 1)
                .set("updatedAt", Instant.now()), User.class);
        if (activeAdmins() == 0) {
            mongo.updateFirst(query(where("_id").is(id)), new Update().set("role", Role.ADMIN), User.class);
            throw ApiException.conflict("LAST_ADMIN", "The last active administrator cannot be demoted");
        }
        Map<String, Object> meta = reason(req.reason());
        meta.put("from", target.getRole().name());
        meta.put("to", role.name());
        audit.record(admin, "ROLE_CHANGED", "USER", id, target.getEmail(), meta);
        return row(find(id), usage(List.of(id)).get(id));
    }

    private long activeAdmins() {
        return users.countByRoleAndActive(Role.ADMIN, true);
    }

    private User find(String id) {
        if (!MongoSupport.isId(id)) throw ApiException.notFound("User");
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    /** ownerId -> [datasets, reviews] in one aggregation. */
    private Map<String, long[]> usage(List<String> ownerIds) {
        Map<String, long[]> out = new HashMap<>();
        if (ownerIds.isEmpty()) return out;
        for (Document d : mongo.getCollection("datasets").aggregate(List.of(
                new Document("$match", new Document("ownerId", new Document("$in", MongoSupport.oids(ownerIds)))),
                new Document("$group", new Document("_id", "$ownerId").append("n", new Document("$sum", 1))
                        .append("reviews", new Document("$sum", "$totalReviews")))))) {
            out.put(MongoSupport.hex(d.get("_id")), new long[]{MongoSupport.lng(d.get("n")), MongoSupport.lng(d.get("reviews"))});
        }
        return out;
    }

    private static AdminUserRow row(User u, long[] usage) {
        return new AdminUserRow(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive(), u.getCreatedAt(),
                u.getLastLoginAt(), usage == null ? 0 : usage[0], usage == null ? 0 : usage[1]);
    }

    private static Role parseRole(String role) {
        try {
            return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown role: " + role);
        }
    }

    private static Map<String, Object> reason(String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) m.put("reason", reason.trim());
        return m;
    }
}
