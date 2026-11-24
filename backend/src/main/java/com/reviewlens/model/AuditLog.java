package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Collection "auditLogs". Immutable record: the application only ever inserts audit entries
 * (AuditService exposes no update or delete operation). Never contains passwords or tokens.
 */
@Document(collection = "auditLogs")
@CompoundIndexes({
        @CompoundIndex(name = "ts", def = "{'timestamp': -1}"),
        @CompoundIndex(name = "action_ts", def = "{'action': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "actor_ts", def = "{'actorId': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "resource_ts", def = "{'resourceId': 1, 'timestamp': -1}")
})
public record AuditLog(
        @Id String id,
        String actorId,
        String actorEmail,
        String action,
        String resourceType,
        String resourceId,
        String resourceLabel,
        Map<String, Object> metadata,
        String ipAddress,
        Instant timestamp) {
}
