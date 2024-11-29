package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobWithDependencies;
import com.doppelganger113.commandrunner.batching.job.factories.JobFactory;
import com.doppelganger113.commandrunner.batching.job.factories.JobWithDependenciesFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
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

    public record Pair(String name, String uniqueJobId) {
    }

    @PersistenceContext
    private EntityManager em;

    private static String buildInValuesClause(List<Job> jobs) {
        StringBuilder valuesClause = new StringBuilder();
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            valuesClause
                    .append("('")
                    .append(job.getName())
                    .append("', '")
                    .append(job.getUniqueJobId())
                    .append("')");
            if (i < jobs.size() - 1) {
                valuesClause.append(", ");
            }
        }

        return valuesClause.toString();
    }

    public List<Job> findJobsByPairs(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return List.of(); // Return empty result if no pairs
        }

        // Build VALUES part of the query dynamically
        StringBuilder valuesClause = new StringBuilder();
        for (int i = 0; i < jobs.size(); i++) {
            valuesClause.append("(:name").append(i).append(", :uniqueId").append(i).append(")");
            if (i < jobs.size() - 1) {
                valuesClause.append(", ");
            }
        }

        String sql = """
            WITH input_pairs(name, unique_job_id) AS (VALUES %s)
            SELECT j.*
            FROM jobs j
            WHERE (j.name, j.unique_job_id) IN (SELECT * FROM input_pairs)
        """.formatted(valuesClause);

        log.info("FORM: {}", sql);

        Query query = em.createNativeQuery(sql, Job.class);

        // Set parameters dynamically
        for (int i = 0; i < jobs.size(); i++) {
            query.setParameter("name" + i, jobs.get(i).getName());
            query.setParameter("uniqueId" + i, jobs.get(i).getUniqueJobId());
        }

        return query.getResultList();
    }

    @Transactional
    public JobCreationResult save(Job newJob) {
        List<Job> jobs = newJob.flatten();

        // TODO: later optimize to be a DB update
        List<Job> existingJobs = findJobsByPairs(jobs);
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

        // TODO: needs logic for creation of new jobs
        if(existingJobs.size() == jobs.size()) {
            JobWithDependencies jobWithDependencies = JobWithDependenciesFactory.fromJobs(existingJobs).orElseThrow();
            if (jobWithDependencies.areAllDependenciesReferences()) {
                // Special scenario where all jobs are references, maybe then we fetch the id?
                return new JobCreationResult(jobWithDependencies, false);
            }
        }

        jobRepository.save(newJob);

        return new JobCreationResult(JobWithDependenciesFactory.from(newJob), true);
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
