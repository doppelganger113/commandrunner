package com.doppelganger113.commandrunner.batching.job.dto;

import com.doppelganger113.commandrunner.batching.job.Job;
import com.doppelganger113.commandrunner.batching.job.JobPersistenceService;

public record JobExecutionResponse(
        Job job,
        JobExecutionDescription description
) {
    public static JobExecutionResponse from(JobPersistenceService.JobCreationResult result) {
        JobExecutionDescription description = switch (result.job().getState()) {
            case RUNNING, STOPPING, READY -> JobExecutionDescription.RUNNING;
            case STOPPED, FAILED, COMPLETED -> JobExecutionDescription.COMPLETED;
        };
        if (result.wasCreated()) {
            description = JobExecutionDescription.CREATED;
        }

        return new JobExecutionResponse(result.job(), description);
    }
}
