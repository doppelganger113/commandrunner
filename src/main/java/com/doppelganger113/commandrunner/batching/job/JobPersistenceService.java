package com.doppelganger113.commandrunner.batching.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class JobPersistenceService {

    private final Logger log = LoggerFactory.getLogger(JobPersistenceService.class);

    public static final List<JobState> JOB_DONE_STATES = List.of(JobState.COMPLETED, JobState.FAILED, JobState.STOPPED);

    private final JobRepository jobRepository;

    public JobPersistenceService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public record JobCreationResult(Job job, boolean wasCreated) {
    }

    @Transactional(timeout = 3)
    public JobCreationResult createNewJobOrGetExisting(Job newJob) {
        // TODO: 1. update persistence to add only jobs that do not exist
        // TODO: 2. use ids of existing ones to replace running ones
        // TODO: 3. retrieve all the jobs related

        // Jobs of same name and same arguments are only executed once
        Optional<Job> existingJob = jobRepository
                .findFirstByNameAndArgumentsHashOrderByIdDesc(newJob.getName(), newJob.getArgumentsHash());
        if (existingJob.isPresent()) {
            return new JobCreationResult(existingJob.get(), false);
        }

        // Jobs of same name and different arguments can be executed multiple times, BUT not at the same time!
        Optional<Job> existingOngoingJob = jobRepository.findByNameAndStateNotInOrderByCreatedAtDesc(
                newJob.getName(), JOB_DONE_STATES
        );
        if (existingOngoingJob.isPresent()) {
            return new JobCreationResult(existingOngoingJob.get(), false);
        }

        Job createdJob = jobRepository.save(newJob);
        return new JobCreationResult(createdJob, true);
    }

    /**
     * Was job actually marked as started as there can be cases where it was stopped before running.
     */
    @Transactional(timeout = 3)
    public boolean setJobToRunning(Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        if (Objects.equals(job.getState(), JobState.RUNNING)) {
            throw new RuntimeException("Job is already running " + job.getId());
        }
        log.debug("setJobToRunning - {}", job);
        if (job.getState().equals(JobState.STOPPING)) {
            jobRepository.setJobStopped(jobId);
            return false;
        }

        jobRepository.setJobStarted(jobId);
        return true;
    }

    @Transactional(timeout = 3)
    public void setJobToStopped(Long jobId) {
        jobRepository.setJobStopped(jobId);
    }

    @Transactional(timeout = 3)
    public void setJobToCompletedOrStopped(Job job) {
        if (job.getState().equals(JobState.STOPPING)) {
            jobRepository.setJobStopped(job.getId());
            return;
        }
        if (!Objects.equals(job.getState(), JobState.RUNNING)) {
            throw new RuntimeException("Job " + job.getId() + " is not in running state but " + job.getState());
        }
        jobRepository.setJobCompleted(job.getId());
    }

    @Transactional(timeout = 3)
    public void setJobToCompletedOrStopped(Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        setJobToCompletedOrStopped(job);
    }

    @Transactional(timeout = 3)
    public void setJobToFailed(Long jobId, Throwable throwable) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
        boolean isStopped = Objects.equals(job.getState(), JobState.STOPPED);
        if (isStopped) {
            throw new RuntimeException("Job " + job.getId() + " is not in state to be stopped: " + job.getState());
        }
        String error = throwable.getLocalizedMessage() + " " + Arrays.toString(throwable.getStackTrace());
        jobRepository.setJobFailed(jobId, error);
    }
}
