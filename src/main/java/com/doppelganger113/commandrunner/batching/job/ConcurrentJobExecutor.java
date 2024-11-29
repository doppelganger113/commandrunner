package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.processors.JobProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * In charge to allow only 1 executor per job.
 */
@Component
public class ConcurrentJobExecutor implements DisposableBean {

    @Value("${server.shutdown:immediate}")
    private String gracefulShutdown;

    private final Logger log = LoggerFactory.getLogger(ConcurrentJobExecutor.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final JobExecutor jobExecutor;
    private final HashMap<String, Semaphore> semaphoreMap;

    public ConcurrentJobExecutor(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
        this.semaphoreMap = createSemaphoreMapFromJobs(jobExecutor);
    }

    @Override
    public void destroy() throws Exception {
        if (!(Objects.equals(gracefulShutdown, "graceful"))) {
            return;
        }
        executor.shutdown();
        boolean hasTimedOut = executor.awaitTermination(15, TimeUnit.SECONDS);
        if (hasTimedOut) {
            log.warn("Timed out waiting for job executor to shutdown");
        }
    }

    private HashMap<String, Semaphore> createSemaphoreMapFromJobs(JobExecutor jobExecutor) {
        HashMap<String, Semaphore> semaphoreMap = new HashMap<>();
        jobExecutor.getAvailableJobs().forEach(js ->
                semaphoreMap.putIfAbsent(js.name(), new Semaphore(1))
        );
        return semaphoreMap;
    }

    public Set<JobExecutor.JobSettings> getAvailableJobs() {
        return jobExecutor.getAvailableJobs();
    }

    public boolean hasJobProcessor(String name) {
        return jobExecutor.hasExecutor(name);
    }

    /**
     * Main usage is for tests to insert dummy processors. Note that it is not concurrency safe
     */
    public void addJobProcessor(JobProcessor jobProcessor) {
        jobExecutor.addJobProcessor(jobProcessor);
        semaphoreMap.putIfAbsent(jobProcessor.getName(), new Semaphore(1));
    }

    public void tryExecuteAsync(@NonNull Job job) {
        log.debug("Trying to execute job {}", job.getId());
        executor.execute(() -> tryExecute(job));
    }

    public void tryExecute(@NonNull Job job) {
        Semaphore semaphore = semaphoreMap.get(job.getName());
        if (semaphore == null) {
            throw new RuntimeException("Job " + job.getName() + " is not available");
        }

        try {
            boolean permit = semaphore.tryAcquire();
            if (!permit) {
                log.debug("Was not able to acquire permission to execute job, skipping {}", job.getName());
                return;
            }
            jobExecutor.execute(job);
        } finally {
            log.debug("Released a lock on the job {}", job.getName());
            semaphore.release();
        }
    }
}
