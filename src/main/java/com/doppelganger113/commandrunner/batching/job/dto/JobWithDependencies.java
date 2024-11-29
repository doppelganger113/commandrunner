package com.doppelganger113.commandrunner.batching.job.dto;

import com.doppelganger113.commandrunner.batching.job.JobState;

import java.time.LocalDateTime;
import java.util.*;

// TODO: see about OpenApi docs on fields
public record JobWithDependencies(
        Long id,
        String name,
        HashMap<String, Object> arguments,
        String uniqueJobId,
        JobState state,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMs,
        Integer retryCount,
        Integer retryLimit,
        Long parentJobId,
        List<JobWithDependencies> dependencies,
        Long referenceJobId,
        String error
) {

    public JobWithDependencies {
        Objects.requireNonNull(dependencies);
    }

    public int size() {
        int sum = 1;
        for (JobWithDependencies child : dependencies) {
            sum += child.size();
        }
        return sum;
    }

    public JobWithDependencies copy(JobWithDependencies other) {
        List<JobWithDependencies> newDependencies = new ArrayList<>();

        if (!other.dependencies.isEmpty()) {
            newDependencies = other.dependencies.stream()
                    .map(this::copy)
                    .toList();
        }

        return new JobWithDependencies(
                other.id(),
                other.name(),
                other.arguments(),
                other.uniqueJobId(),
                other.state(),
                other.createdAt(),
                other.updatedAt(),
                other.startedAt(),
                other.completedAt(),
                other.durationMs(),
                other.retryCount(),
                other.retryLimit(),
                other.parentJobId(),
                newDependencies,
                other.referenceJobId(),
                other.error()
        );
    }

    public boolean areAllDependenciesReferences() {
        if (referenceJobId == null) {
            return false;
        }
        for (JobWithDependencies child : dependencies) {
            if (!child.areAllDependenciesReferences()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns an array of leaf nodes that do not have children with references, if all children
     */
    public List<JobWithDependencies> findNonRefLeafDependencies() {
        if (dependencies.isEmpty()) {
            if (this.referenceJobId == null) {
                return List.of(this);
            }

            return Collections.emptyList();
        }

        List<JobWithDependencies> result = new ArrayList<>();

        for (JobWithDependencies child : dependencies) {
            if (child.dependencies.isEmpty()) {
                if (child.referenceJobId == null) {
                    result.add(child);
                }
            } else {
                List<JobWithDependencies> childLeafDeps = child.findNonRefLeafDependencies();
                if(childLeafDeps.isEmpty()) {
                    if(child.referenceJobId == null) {
                        result.add(child);
                    }
                } else {
                    result.addAll(child.findNonRefLeafDependencies());
                }
            }
        }

        return result;
    }
}
