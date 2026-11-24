package com.reviewlens.support;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sliding-window limiter (single instance) for sign-in attempts and the public demo. */
@Component
public class RateLimiter {
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** Records a hit if fewer than {@code limit} hits happened inside the window. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, window);
            if (q.size() >= limit) return false;
            q.addLast(System.currentTimeMillis());
            return true;
        }
    }

    public int count(String key, Duration window) {
        Deque<Long> q = hits.get(key);
        if (q == null) return 0;
        synchronized (q) {
            prune(q, window);
            return q.size();
        }
    }

    public void hit(String key) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(System.currentTimeMillis());
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), Duration.ofHours(1));
                return e.getValue().isEmpty();
            }
        });
    }

    private static void prune(Deque<Long> q, Duration window) {
        long cutoff = System.currentTimeMillis() - window.toMillis();
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
    }
}
