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
import java.util.Set;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobPersistenceService jobPersistenceService;
    private final ConcurrentJobExecutor jobExecutor;
    private final JobFactory jobFactory;


    public JobService(
            JobRepository jobRepository,
            JobPersistenceService jobPersistenceService,
            ConcurrentJobExecutor jobExecutor,
            JobFactory jobFactory
    ) {
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

    public Set<JobExecutor.JobSettings> getAvailableJobs() {
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
                .filter(name -> !jobExecutor.hasJobProcessor(name))
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
                CreateJobResult.CREATED : CreateJobResult.REFERENCED;

        if (creationResult.wasPersisted()) {
            creationResult.job().findNonRefLeafDependencies()
                    .forEach(leafJob -> jobExecutor.tryExecuteAsync(newJob));
        }

        return new CreateJobResponse(creationResult.job(), result);
    }
}
