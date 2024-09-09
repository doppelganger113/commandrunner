package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.batching.job.dto.JobUpdate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("jobs")
public class JobController {

    private final JobService jobService;
    private final JobRepository jobRepository;

    public JobController(JobService jobService, JobRepository jobRepository) {
        this.jobService = jobService;
        this.jobRepository = jobRepository;
    }

    @GetMapping
    public List<Job> getJobs() {
        return jobService.findAll();
    }

    @GetMapping("/available")
    public List<JobExecutor.JobSettings> getExistingJobs() {
        return jobService.getAvailableJobs();
    }

    @GetMapping("/{id}")
    public Job getJob(@PathVariable long id) {
        return jobRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PatchMapping("/{id}")
    public void updateJob(@PathVariable long id, @RequestBody JobUpdate jobUpdate) {
        if (jobUpdate.stop()) {
            jobRepository.setJobToStop(id);
        }
    }

    @PostMapping
    public Job execute(@RequestBody JobExecutionOptions jobExecutionOptions) {
        // WHOLE discussion is on: should we return an existing job or only if there is a new one created?
        //      This is in cases this is used through an API where we create a job with bunch of tasks

        // [IF] we want to rerun the same job with same arguments again, we just add a new argument that's a date
        // and because date is changed for every request it will get executed every request as a new job
        // but of course waiting for the previous of the same name to finish

        // Are there cases when we want a job with the same name (diff args) to run in parallel?

        // 1. job with the same name and arguments was already executed, we do nothing
        // 2. job with the same name and DIFFERENT arguments was executed, we create a new job

        // 3. job with the same name and arguments is already running
        // 4. job with the same name and DIFFERENT arguments is already running

        // 5. job with the same name and same arguments is finished

        // We return the response with:
        // CASE: "RUNNING"
        // CASE: "STOPPING"
        // CASE: "COMPLETED"
        return jobService.executeJob(jobExecutionOptions);
    }
}
