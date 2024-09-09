package com.doppelganger113.commandrunner.batching.job;


import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.hash.ShaHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class JobPersistenceService {

    private final Logger log = LoggerFactory.getLogger(JobPersistenceService.class);

    public static final List<JobState> JOB_DONE_STATES = List.of(JobState.COMPLETED, JobState.FAILED, JobState.STOPPED);

    private final JobRepository jobRepository;
    private final ShaHash shaHash;

    public JobPersistenceService(JobRepository jobRepository, ShaHash shaHash) {
        this.jobRepository = jobRepository;
        this.shaHash = shaHash;
    }

    public record JobCreationResult(Job job, boolean wasCreated) {
    }

    @Transactional
    public JobCreationResult createNewJobOrGetExisting(JobExecutionOptions jobExecutionOptions) {
        Job newJob = new Job();
        newJob.setName(jobExecutionOptions.name());
        newJob.setArguments(jobExecutionOptions.arguments());
        newJob.setArgumentsHash(shaHash.hash(jobExecutionOptions.arguments()));
        newJob.setState(JobState.READY);

        Optional<Job> existingJob = jobRepository
                .findFirstByNameAndArgumentsHashOrderByIdDesc(newJob.getName(), newJob.getArgumentsHash());
        if (existingJob.isPresent()) {
            return new JobCreationResult(existingJob.get(), false);
        }

        Job createdJob = jobRepository.save(newJob);
        return new JobCreationResult(createdJob, true);
    }

    /**
     * Was job actually marked as started as there can be cases where it was stopped before running.
     */
    @Transactional
    public boolean setJobToRunning(Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        if (Objects.equals(job.getState(), JobState.RUNNING)) {
            throw new RuntimeException("Job is already running " + job.getId());
        }
        log.info("setJobToRunning - {}", job);
        if (job.getState().equals(JobState.STOPPING)) {
            jobRepository.setJobStopped(jobId);
            return false;
        }

        jobRepository.setJobStarted(jobId);
        return true;
    }

    @Transactional
    public void setJobToStopped(Long jobId) {
        jobRepository.setJobStopped(jobId);
    }

    @Transactional
    public void setJobToCompletedOrStopped(Job job) {
        setJobToCompletedOrStopped(job.getId());
        if (!Objects.equals(job.getState(), JobState.RUNNING)) {
            throw new RuntimeException("Job " + job.getId() + " is not in running state but " + job.getState());
        }
        if (job.getState().equals(JobState.STOPPING)) {
            jobRepository.setJobStopped(job.getId());
            return;
        }
        jobRepository.setJobCompleted(job.getId());
    }

    @Transactional
    public void setJobToCompletedOrStopped(Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        setJobToCompletedOrStopped(job);
    }

    @Transactional
    public void setJobToFailed(Long jobId, Throwable throwable) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        boolean isCompleted = Objects.equals(job.getState(), JobState.RUNNING);
        boolean isStopped = Objects.equals(job.getState(), JobState.STOPPED);
        if (isCompleted || isStopped) {
            throw new RuntimeException("Job " + job.getId() + " is not in state to be stopped: " + job.getState());
        }
        String error = throwable.getLocalizedMessage() + Arrays.toString(throwable.getStackTrace());
        jobRepository.setJobFailed(jobId, error);
    }
}
