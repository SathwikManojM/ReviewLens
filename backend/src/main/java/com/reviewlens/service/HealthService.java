package com.reviewlens.service;

import com.reviewlens.dto.AdminDtos.ServiceStatus;
import com.reviewlens.dto.AdminDtos.SystemHealth;
import com.reviewlens.model.AnalysisJob;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.repository.AnalysisJobRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Service availability for the admin portal. Never exposes connection strings, credentials or secrets. */
@Service
public class HealthService {
    private final MongoTemplate mongo;
    private final NlpClient nlp;
    private final AnalysisJobRepository jobs;

    public HealthService(MongoTemplate mongo, NlpClient nlp, AnalysisJobRepository jobs) {
        this.mongo = mongo;
        this.nlp = nlp;
        this.jobs = jobs;
    }

    public SystemHealth check() {
        List<ServiceStatus> services = List.of(api(), database(), nlpEngine());
        boolean allUp = services.stream().allMatch(s -> "UP".equals(s.status()));
        return new SystemHealth(allUp ? "OPERATIONAL" : "DEGRADED", Instant.now(), services);
    }

    public boolean databaseUp() {
        try {
            mongo.executeCommand(new Document("ping", 1));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ServiceStatus api() {
        Runtime rt = Runtime.getRuntime();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("uptimeSeconds", uptimeMs / 1000);
        d.put("startedAt", Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()).toString());
        d.put("javaVersion", System.getProperty("java.version"));
        d.put("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        d.put("heapMaxMb", rt.maxMemory() / (1024 * 1024));
        d.put("processors", rt.availableProcessors());
        try {
            d.put("runningJobs", jobs.countByStatus(AnalysisJob.Status.RUNNING));
            d.put("queuedJobs", jobs.countByStatus(AnalysisJob.Status.QUEUED));
        } catch (RuntimeException ignored) { }
        String version = getClass().getPackage().getImplementationVersion();
        return new ServiceStatus("Spring Boot API", "UP", 0L, version == null ? "2.0.0-dev" : version, d);
    }

    private ServiceStatus database() {
        Map<String, Object> d = new LinkedHashMap<>();
        try {
            long t0 = System.nanoTime();
            mongo.executeCommand(new Document("ping", 1));
            long latency = (System.nanoTime() - t0) / 1_000_000;
            Document build = mongo.executeCommand(new Document("buildInfo", 1));
            d.put("database", mongo.getDb().getName());
            d.put("collections", mongo.getCollectionNames().size());
            return new ServiceStatus("MongoDB", "UP", latency, build.getString("version"), d);
        } catch (RuntimeException e) {
            d.put("error", "Connection failed (" + e.getClass().getSimpleName() + ")");
            return new ServiceStatus("MongoDB", "DOWN", null, null, d);
        }
    }

    private ServiceStatus nlpEngine() {
        Map<String, Object> d = new LinkedHashMap<>();
        try {
            long t0 = System.nanoTime();
            NlpClient.Health h = nlp.health();
            long latency = (System.nanoTime() - t0) / 1_000_000;
            Map<String, Object> engine = h == null || h.engine() == null ? Map.of() : h.engine();
            d.put("engine", engine.getOrDefault("name", "rule-based-vader"));
            d.put("type", engine.getOrDefault("type", "baseline"));
            d.put("aspectCategories", h == null || h.aspects() == null ? 0 : h.aspects().size());
            return new ServiceStatus("Python NLP engine", "UP", latency, String.valueOf(engine.getOrDefault("version", "1.x")), d);
        } catch (RuntimeException e) {
            d.put("error", "Not reachable (" + e.getClass().getSimpleName() + ")");
            return new ServiceStatus("Python NLP engine", "DOWN", null, null, d);
        }
    }
}
