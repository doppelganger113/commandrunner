package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.processors.EmptyRunner;
import com.doppelganger113.commandrunner.batching.job.processors.JobProcessor;
import com.doppelganger113.commandrunner.batching.job.processors.JobRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableAsync
public class JobExecutor {

    private final Logger log = LoggerFactory.getLogger(JobExecutor.class);

    private final JobPersistenceService jobPersistenceService;

    public ConcurrentHashMap<String, JobProcessor> map = createMapFromJobProcessors(List.of(
            new JobRunner(),
            new EmptyRunner()
    ));

    public JobExecutor(JobPersistenceService jobPersistenceService) {
        this.jobPersistenceService = jobPersistenceService;
    }

    private ConcurrentHashMap<String, JobProcessor> createMapFromJobProcessors(List<JobProcessor> jobProcessors) {
        HashMap<String, JobProcessor> map = new HashMap<>();
        jobProcessors.forEach(jobProcessor -> map.putIfAbsent(jobProcessor.getName(), jobProcessor));

        return new ConcurrentHashMap<>(map);
    }

    /**
     * Mostly used for tests to add custom job processors.
     */
    public void addJobProcessor(JobProcessor jobProcessor) {
        map.putIfAbsent(jobProcessor.getName(), jobProcessor);
    }

    public void removeJobProcessor(JobProcessor jobProcessor) {
        map.remove(jobProcessor.getName());
    }

    public void replaceJobProcessor(JobProcessor jobProcessor) {
        map.replace(jobProcessor.getName(), jobProcessor);
    }

    public Optional<JobProcessor> getJobRunnerByName(String name) {
        return Optional.ofNullable(map.get(name));
    }

    public boolean hasExecutor(String executorName) {
        return map.containsKey(executorName);
    }

    public record JobSettings(String name) {
    }

    public List<JobSettings> getAvailableJobs() {
        return map.keySet().stream()
                .map(JobSettings::new)
                .toList();
    }

    @Async
    public void execute(@NonNull Job job) {
        Objects.requireNonNull(job);
        var args = job.getArguments();
        var jobId = job.getId();
        Objects.requireNonNull(jobId);

        JobProcessor jobRunner = getJobRunnerByName(job.getName())
                .orElseThrow(() -> new RuntimeException("No job runner found with name " + job.getName()));

        log.debug("job runner execution {}", jobId);

        try {

            jobRunner.before(args);

            boolean wasStarted = jobPersistenceService.setJobToRunning(jobId);
            if (!wasStarted) {
                log.debug("job runner skipped due to being stopped: {}", jobId);
                jobPersistenceService.setJobToStopped(jobId);
                return;
            }
            log.debug("job runner started: {}", jobId);

            jobRunner.execute(args);

            jobPersistenceService.setJobToCompletedOrStopped(jobId);
            log.debug("job runner finished: {}", jobId);
        } catch (RuntimeException e) {
            log.error("job runner failed: {}", jobId, e);
            jobPersistenceService.setJobToFailed(jobId, e);
        } finally {
            jobRunner.after(args);
        }
    }
}
