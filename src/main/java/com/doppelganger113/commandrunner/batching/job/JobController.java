package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.*;
import jakarta.validation.constraints.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("jobs")
public class JobController {

    private static final Logger log = LogManager.getLogger(JobController.class);
    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public List<JobDto> getJobs() {
        MDC.put("job_name", "my job");
        log.info("Logging and testing jobs {}", new JobExecutionOptions("my job", null, null));
        return jobService.findAll();
    }

    @GetMapping("/available")
    public List<JobExecutor.JobSettings> getExistingJobs() {
        return jobService.getAvailableJobs();
    }

    @GetMapping("/{id}")
    public JobDto getJob(@PathVariable long id) {
        return jobService.findById(id)
                .map(JobDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateJob(@PathVariable long id, @RequestBody JobUpdate jobUpdate) {
        if (jobUpdate.stop()) {
            jobService.stopById(id);
        }
    }

    @GetMapping("/{id}/dependencies")
    public JobNode getDependencies(@PathVariable long id) {
        return jobService.getJobAndDependenciesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public JobExecutionResponse execute(@NotNull @RequestBody JobExecutionOptions jobExecutionOptions) {
        return jobService.executeJob(jobExecutionOptions);
    }
}
