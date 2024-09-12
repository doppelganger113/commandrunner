package com.doppelganger113.commandrunner.batching.job.dto;

import com.doppelganger113.commandrunner.batching.job.Job;
import com.doppelganger113.commandrunner.batching.job.JobState;

import java.time.LocalDateTime;
import java.util.HashMap;

public record JobDto(
        Long id,
        String name,
        HashMap<String, Object> arguments,
        String argumentsHash,
        JobState state,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMs,
        Integer retryCount,
        Integer retryLimit,
        Long parentJobId,
        String error
) {
    public static JobDto from(Job job) {
        return new JobDto(
                job.getId(),
                job.getName(),
                job.getArguments(),
                job.getArgumentsHash(),
                job.getState(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getDurationMs(),
                job.getRetryCount(),
                job.getRetryLimit(),
                job.getParentJobId(),
                job.getError()
        );
    }
}
