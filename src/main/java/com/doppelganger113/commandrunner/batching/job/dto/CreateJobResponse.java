package com.doppelganger113.commandrunner.batching.job.dto;

public record CreateJobResponse(
        JobWithDependencies data,
        CreateJobResult result
) {
}
