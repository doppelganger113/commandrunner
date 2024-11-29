package com.doppelganger113.commandrunner.batching.job.factories;

import com.doppelganger113.commandrunner.batching.job.Job;
import com.doppelganger113.commandrunner.batching.job.dto.JobWithDependencies;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class JobWithDependenciesFactory {

    /**
     *
     * @param job Job that is expected to have children.
     */
    public static JobWithDependencies from(@NotNull Job job) {
        Objects.requireNonNull(job, "job cannot be null");

        List<JobWithDependencies> dependencies = new ArrayList<>();
        if (!job.getChildren().isEmpty()) {
            dependencies = job.getChildren().stream()
                    .map(JobWithDependenciesFactory::from)
                    .toList();
        }

        return new JobWithDependencies(
                job.getId(),
                job.getName(),
                job.getArguments(),
                job.getUniqueJobId(),
                job.getState(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getDurationMs(),
                job.getRetryCount(),
                job.getRetryLimit(),
                job.getParentJobId(),
                dependencies,
                job.getReferenceJobId(),
                job.getError()
        );
    }

    private static Optional<JobWithDependencies> findParentById(JobWithDependencies node, Long parentId) {
        if (node.id().equals(parentId)) {
            return Optional.of(node);
        }

        // search first level
        for (JobWithDependencies child : node.dependencies()) {
            if (child.id().equals(parentId)) {
                return Optional.of(child);
            }
        }
        for (JobWithDependencies child : node.dependencies()) {
            Optional<JobWithDependencies> parentNode = findParentById(child, parentId);
            if (parentNode.isPresent()) {
                return parentNode;
            }
        }

        return Optional.empty();
    }

    private static void checkOrphansAndTryAddToParent(JobWithDependencies parent, List<Job> orphanJobs) {
        for (Iterator<Job> jobIterator = orphanJobs.iterator(); jobIterator.hasNext(); ) {
            Job orphan = jobIterator.next();
            if (orphan.getParentJobId() != null && orphan.getParentJobId().equals(parent.id())) {
                JobWithDependencies newNode = from(orphan);
                parent.dependencies().add(newNode);
                jobIterator.remove();
                checkOrphansAndTryAddToParent(newNode, orphanJobs);
            }
        }
    }

    /**
     * Constructs a job tree hierarchy based on the <strong>parent_id</strong> field
     * something like:
     * <pre>
     * 1 -->
     *      2 -->
     *          4
     *          10
     *      3 -->
     *          7 -->
     *              9
     *          8
     * </pre>
     */
    public static Optional<JobWithDependencies> fromJobs(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return Optional.empty();
        }
        List<Job> nonNullJobs = jobs.stream()
                .filter(Objects::nonNull)
                .toList();
        if (nonNullJobs.size() == 1) {
            return Optional.of(from(nonNullJobs.getFirst()));
        }

        Job rootJob = nonNullJobs.stream()
                .filter(job -> job.getParentJobId() == null)
                .findFirst()
                .orElseThrow();
        JobWithDependencies rootNode = from(rootJob);
        List<Job> orphanJobs = new ArrayList<>(nonNullJobs.size());

        for (Job job : nonNullJobs) {
            JobWithDependencies parent = findParentById(rootNode, job.getParentJobId()).orElse(null);
            if (parent == null) {
                orphanJobs.add(job);
                continue;
            }

            JobWithDependencies newNode = from(job);
            parent.dependencies().add(newNode);
            if (orphanJobs.isEmpty()) {
                continue;
            }

            checkOrphansAndTryAddToParent(newNode, orphanJobs);
        }

        if (rootNode.size() != nonNullJobs.size()) {
            throw new RuntimeException("Could not fully form a JobNode, got difference of: " +
                    (nonNullJobs.size() - rootNode.size())
            );
        }

        return Optional.of(rootNode);
    }

    /**
     * Constructs a job tree hierarchy based on the parent id, something like:
     * <pre>
     * 1 -->
     *      2 -->
     *          4
     *          10
     *      3 -->
     *          7 -->
     *              9
     *          8
     * </pre>
     * Returns following array structure:
     * <pre>
     *     [0] -> 4, 10
     *     [1] -> 9
     *     [2] -> 8
     * </pre>
     */
    public static List<JobWithDependencies> findLeafNodes(JobWithDependencies rootNode) {
        if (rootNode.dependencies().isEmpty()) {
            return List.of(rootNode);
        }

        List<JobWithDependencies> leafNodes = new ArrayList<>();
        for (JobWithDependencies child : rootNode.dependencies()) {
            if (child.dependencies().isEmpty()) {
                leafNodes.add(child);
                continue;
            }
            leafNodes.addAll(findLeafNodes(child));
        }

        return leafNodes;
    }
}
