package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.processors.EmptyRunner;
import com.doppelganger113.commandrunner.batching.job.processors.JobProcessor;
import com.doppelganger113.commandrunner.batching.job.processors.JobRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public void execute(Job job) {
        Objects.requireNonNull(job.getId());
        JobProcessor jobRunner = getJobRunnerByName(job.getName())
                .orElseThrow(() -> new RuntimeException("No job runner found with name " + job.getName()));

        log.debug("job runner execution {}", job.getId());
        try {
            boolean wasStarted = jobPersistenceService.setJobToRunning(job.getId());
            if (!wasStarted) {
                log.debug("job runner skipped due to being stopped");
                jobPersistenceService.setJobToStopped(job.getId());
                return;
            }
            log.debug("job runner started");
            jobRunner.execute(job.getArguments());
            jobPersistenceService.setJobToCompletedOrStopped(job.getId());
            log.debug("job runner finished");
        } catch (RuntimeException e) {
            log.error("job runner failed", e);
            jobPersistenceService.setJobToFailed(job.getId(), e);
        }
    }
}
