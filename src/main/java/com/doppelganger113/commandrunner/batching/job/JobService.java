package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class JobService {

    private final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobPersistenceService jobPersistenceService;
    private final JobExecutor jobExecutor;

    public JobService(JobRepository jobRepository, JobPersistenceService jobPersistenceService, JobExecutor jobExecutor) {
        this.jobRepository = jobRepository;
        this.jobPersistenceService = jobPersistenceService;
        this.jobExecutor = jobExecutor;
    }

    public List<Job> findAll() {
        return jobRepository.findAll();
    }

    public List<JobExecutor.JobSettings> getAvailableJobs() {
        return jobExecutor.getAvailableJobs();
    }

    public JobExecutionResponse executeJob(JobExecutionOptions jobExecutionOptions) {
        if (!jobExecutor.hasExecutor(jobExecutionOptions.name())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Job executor with name "
                            + jobExecutionOptions.name()
                            + " does not exist, check /jobs/available for available jobs"
            );
        }

        JobPersistenceService.JobCreationResult result = jobPersistenceService.createNewJobOrGetExisting(jobExecutionOptions);
        if (result.wasCreated()) {
            jobExecutor.execute(result.job());
        }

        return JobExecutionResponse.from(result);
    }
}
