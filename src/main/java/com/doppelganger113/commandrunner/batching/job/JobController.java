package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionResponse;
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateJob(@PathVariable long id, @RequestBody JobUpdate jobUpdate) {
        if (jobUpdate.stop()) {
            jobRepository.setJobToStop(id);
        }
    }

    @PostMapping
    public JobExecutionResponse execute(@RequestBody JobExecutionOptions jobExecutionOptions) {
        return jobService.executeJob(jobExecutionOptions);
    }
}
