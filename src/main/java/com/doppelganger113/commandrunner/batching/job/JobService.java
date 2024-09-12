package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionResponse;
import com.doppelganger113.commandrunner.batching.job.dto.JobNode;
import com.doppelganger113.commandrunner.batching.job.dto.JobDto;
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

    public Optional<JobNode> getJobAndDependenciesById(Long id) {
        List<Job> jobs = jobRepository.findJobByIdAndItsDependencies(id);
        return JobNode.of(jobs);
    }

    public JobExecutionResponse executeJob(@NotNull JobExecutionOptions jobExecutionOptions) {
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

        JobPersistenceService.JobCreationResult result = jobPersistenceService.createNewJobOrGetExisting(newJob);
        if (result.wasCreated()) {
            jobExecutor.execute(result.job());
        }

        return JobExecutionResponse.from(result);
    }
}
