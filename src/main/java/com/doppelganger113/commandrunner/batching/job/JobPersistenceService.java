package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobWithDependencies;
import com.doppelganger113.commandrunner.batching.job.factories.JobFactory;
import com.doppelganger113.commandrunner.batching.job.factories.JobWithDependenciesFactory;
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
    private final JobFactory jobFactory;

    public JobPersistenceService(JobRepository jobRepository, JobFactory jobFactory) {
        this.jobRepository = jobRepository;
        this.jobFactory = jobFactory;
    }

    public record JobCreationResult(JobWithDependencies job, boolean wasPersisted) {
    }

    public List<Job> getExistingJobs(List<Job> jobs) {
        List<Object[]> nameUniqueJobIdPairs = jobs.stream()
                .map(j -> new Object[]{j.getName(), j.getUniqueJobId()})
                .toList();

        return jobRepository.findJobsByNameAndUniqueJobIdPairs(nameUniqueJobIdPairs);
    }

    @Transactional
    public JobCreationResult save(Job newJob) {
        List<Job> jobs = newJob.flatten();

        List<Job> existingJobs = getExistingJobs(jobs);
        if (!existingJobs.isEmpty()) {
            existingJobs.forEach(existingJob -> {
                newJob.findJobByNameAndUniqueJobId(existingJob.getName(), existingJob.getUniqueJobId())
                        .ifPresent(job -> {
                            job.setReferenceJobId(existingJob.getId());
                            job.setState(JobState.REFERENCED);
                            job.setUniqueJobId(
                                    jobFactory.updateUniqueJobIdForReference(existingJob.getUniqueJobId())
                            );
                        });
            });
        }

        JobWithDependencies jobWithDependencies = JobWithDependenciesFactory.fromJobs(jobs).orElseThrow();
        if(jobWithDependencies.areAllDependenciesReferences()) {
            return new JobCreationResult(jobWithDependencies, false);
        }

        jobs = jobRepository.saveAll(jobs);

        return new JobCreationResult(JobWithDependenciesFactory.fromJobs(jobs).orElseThrow(), true);
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
