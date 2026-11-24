package com.reviewlens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /** Runs analysis jobs; the number running at once is capped by the admin setting maxConcurrentJobs (1-8). */
    @Bean(name = "processingExecutor")
    ThreadPoolTaskExecutor processingExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(32);
        ex.setThreadNamePrefix("analysis-");
        ex.initialize();
        return ex;
    }

    /** Generates library reports (PDF / CSV) in the background. */
    @Bean(name = "reportExecutor")
    ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("report-");
        ex.initialize();
        return ex;
    }
}
