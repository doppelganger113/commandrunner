package com.doppelganger113.commandrunner.batching.job.dto;

public record JobExecutionResponse(
        Long jobId,
        Boolean wasCreated
) {
}
