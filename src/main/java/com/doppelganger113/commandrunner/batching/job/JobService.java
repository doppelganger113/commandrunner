package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.*;
import com.doppelganger113.commandrunner.batching.job.factories.JobFactory;
import com.doppelganger113.commandrunner.batching.job.factories.JobWithDependenciesFactory;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobPersistenceService jobPersistenceService;
    private final JobExecutor jobExecutor;
    private final JobFactory jobFactory;


    public JobService(JobRepository jobRepository, JobPersistenceService jobPersistenceService, JobExecutor jobExecutor, JobFactory jobFactory) {
        this.jobRepository = jobRepository;
        this.jobPersistenceService = jobPersistenceService;
        this.jobExecutor = jobExecutor;
        this.jobFactory = jobFactory;
    }

    @Transactional(readOnly = true)
    public List<JobDto> findAll() {
        return jobRepository.findAll().stream()
                .map(JobDto::from)
                .toList();
    }

    public List<JobExecutor.JobSettings> getAvailableJobs() {
        return jobExecutor.getAvailableJobs();
    }

    public Optional<Job> findById(Long id) {
        return jobRepository.findById(id);
    }

    public void stopById(Long id) {
        jobRepository.setJobToStop(id);
    }

    public Optional<JobWithDependencies> getJobAndDependenciesById(Long id) {
        List<Job> jobs = jobRepository.findJobByIdAndItsDependencies(id);
        return JobWithDependenciesFactory.fromJobs(jobs);
    }

    public CreateJobResponse createJob(@NotNull JobExecutionOptions jobExecutionOptions) {
        Objects.requireNonNull(jobExecutionOptions);
        if (jobExecutionOptions.hasDuplicate()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate job execution options");
        }

        List<String> unsupportedJobs = jobExecutionOptions.flatten().stream()
                .map(JobExecutionOptions::name)
                .filter(name -> !jobExecutor.hasExecutor(name))
                .toList();

        if (!unsupportedJobs.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Jobs: '"
                            + String.join("', '", unsupportedJobs)
                            + "' not exist, check /jobs/available for available jobs"
            );
        }

        Job newJob = jobFactory.from(jobExecutionOptions);

        JobPersistenceService.JobCreationResult creationResult = jobPersistenceService.save(newJob);
        CreateJobResult result = creationResult.wasPersisted() ?
                CreateJobResult.CREATED: CreateJobResult.REFERENCED;

        // TODO: send an event about job creation or maybe not as it runs every 5sec

        return new CreateJobResponse(creationResult.job(), result);
    }
}
