package com.reviewlens.service;

import com.reviewlens.dto.Dtos.JobDto;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.AnalysisJob;
import com.reviewlens.model.Dataset;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.repository.AnalysisJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Persistent analysis-job queue. Jobs are created QUEUED; a dispatcher atomically claims them
 * (findAndModify QUEUED -> RUNNING) while respecting the maxConcurrentJobs setting.
 */
@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    public static final List<AnalysisJob.Status> ACTIVE = List.of(AnalysisJob.Status.QUEUED, AnalysisJob.Status.RUNNING);

    private final AnalysisJobRepository jobs;
    private final MongoTemplate mongo;
    private final SettingsService settings;
    private final NlpClient nlp;
    private final ProcessingService processing;
    private final TaskExecutor executor;
    private volatile boolean dispatcherErrorLogged;

    public JobService(AnalysisJobRepository jobs, MongoTemplate mongo, SettingsService settings, NlpClient nlp,
                      ProcessingService processing, @Qualifier("processingExecutor") TaskExecutor executor) {
        this.jobs = jobs;
        this.mongo = mongo;
        this.settings = settings;
        this.nlp = nlp;
        this.processing = processing;
        this.executor = executor;
    }

    public AnalysisJob enqueue(Dataset d, AnalysisJob.Type type, String triggeredBy, String retryOf) {
        if (jobs.existsByDatasetIdAndStatusIn(d.getId(), ACTIVE)) {
            throw ApiException.conflict("JOB_ACTIVE", "An analysis job is already queued or running for this dataset");
        }
        AnalysisJob j = new AnalysisJob();
        j.setDatasetId(d.getId());
        j.setDatasetName(d.getName());
        j.setOwnerId(d.getOwnerId());
        j.setTriggeredBy(triggeredBy);
        j.setType(type);
        j.setStatus(AnalysisJob.Status.QUEUED);
        j.setStage(AnalysisJob.Stage.QUEUED);
        j.setCreatedAt(Instant.now());
        j.setRetryOfJobId(retryOf);
        j.setNlpEngine(nlp.engineLabel());
        jobs.save(j);
        mongo.updateFirst(query(where("_id").is(d.getId())), new Update()
                .set("status", Dataset.Status.QUEUED).set("latestJobId", j.getId()).set("errorMessage", null)
                .set("processedReviews", 0).set("completedAt", null), Dataset.class);
        d.setStatus(Dataset.Status.QUEUED);
        d.setLatestJobId(j.getId());
        d.setErrorMessage(null);
        d.setProcessedReviews(0);
        return j;
    }

    /** Usage limit: analyses (uploads + re-runs) started by a user's datasets in the last 24 hours. */
    public void checkDailyLimit(String ownerId) {
        int limit = settings.get().getMaxAnalysesPerUserPerDay();
        long recent = mongo.count(query(where("ownerId").is(ownerId).and("createdAt").gte(Instant.now().minus(Duration.ofDays(1)))), AnalysisJob.class);
        if (recent >= limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LIMIT_REACHED",
                    "Daily analysis limit reached (" + limit + " per 24 hours). Try again later.");
        }
    }

    @Scheduled(fixedDelay = 1500, initialDelay = 4000)
    public void dispatch() {
        try {
            int max = settings.get().getMaxConcurrentJobs();
            long running = jobs.countByStatus(AnalysisJob.Status.RUNNING);
            while (running < max) {
                AnalysisJob job = mongo.findAndModify(
                        query(where("status").is(AnalysisJob.Status.QUEUED)).with(Sort.by(Sort.Direction.ASC, "createdAt")),
                        new Update().set("status", AnalysisJob.Status.RUNNING).set("startedAt", Instant.now()),
                        FindAndModifyOptions.options().returnNew(true), AnalysisJob.class);
                if (job == null) break;
                running++;
                executor.execute(() -> processing.run(job));
            }
            dispatcherErrorLogged = false;
        } catch (RuntimeException e) {
            if (!dispatcherErrorLogged) log.warn("Job dispatcher cannot reach MongoDB: {}", e.getMessage());
            dispatcherErrorLogged = true;
        }
    }

    /** Jobs that were RUNNING when the server stopped can never finish - mark them failed so they can be retried. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        try {
            List<AnalysisJob> stale = mongo.find(query(where("status").is(AnalysisJob.Status.RUNNING)), AnalysisJob.class);
            String msg = "Interrupted by a server restart - retry the analysis";
            for (AnalysisJob j : stale) {
                mongo.updateFirst(query(where("_id").is(j.getId())), new Update().set("status", AnalysisJob.Status.FAILED)
                        .set("errorMessage", msg).set("finishedAt", Instant.now()), AnalysisJob.class);
                mongo.updateFirst(query(where("_id").is(j.getDatasetId()).and("latestJobId").is(j.getId())),
                        new Update().set("status", Dataset.Status.FAILED).set("errorMessage", msg), Dataset.class);
            }
            if (!stale.isEmpty()) log.warn("Marked {} interrupted analysis job(s) as failed", stale.size());
        } catch (RuntimeException e) {
            log.warn("Could not recover interrupted jobs: {}", e.getMessage());
        }
    }

    public static JobDto toDto(AnalysisJob j) {
        if (j == null) return null;
        return new JobDto(j.getId(), j.getDatasetId(), j.getDatasetName(), j.getOwnerId(), j.getType().name(),
                j.getStatus().name(), j.getStage() == null ? null : j.getStage().name(), j.getTotalReviews(),
                j.getProcessedReviews(), j.getErrorMessage(), j.getCreatedAt(), j.getStartedAt(), j.getFinishedAt(),
                j.getDurationMs(), j.getTriggeredBy(), j.getRetryOfJobId(), j.getNlpEngine());
    }
}
