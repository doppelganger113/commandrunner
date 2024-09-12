package com.doppelganger113.commandrunner.batching.job.dto;

import com.doppelganger113.commandrunner.batching.job.Job;

import java.util.*;

/**
 * The {@code JobNode} represents a node in a hierarchy tree where a node can have children nodes and there is
 * always a root node. Main usage is through the function {@code JobNode.of} which constructs this hierarchy
 * and returns the root node.
 */
public class JobNode {
    public final Job job;
    public final List<JobNode> children = new ArrayList<>();

    public JobNode(Job job) {
        Objects.requireNonNull(job);
        this.job = job;
    }

    public int size() {
        int sum = 1;
        for (JobNode child : children) {
            sum += child.size();
        }
        return sum;
    }

    private static void checkOrphans(JobNode parent, List<Job> orphanJobs) {
        for (Iterator<Job> jobIterator = orphanJobs.iterator(); jobIterator.hasNext(); ) {
            Job job = jobIterator.next();
            if (job.getParentJobId() != null && job.getParentJobId().equals(parent.job.getId())) {
                JobNode newNode = new JobNode(job);
                parent.children.add(newNode);
                jobIterator.remove();
                checkOrphans(newNode, orphanJobs);
            }
        }
    }

    private static Optional<JobNode> findParentById(JobNode node, Long parentId) {
        if (node.job.getId().equals(parentId)) {
            return Optional.of(node);
        }

        // search first level
        for (JobNode child : node.children) {
            if (child.job.getId().equals(parentId)) {
                return Optional.of(child);
            }
        }
        for (JobNode child : node.children) {
            Optional<JobNode> parentNode = findParentById(child, parentId);
            if (parentNode.isPresent()) {
                return parentNode;
            }
        }

        return Optional.empty();
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
    public static List<JobNode> findLeafNodes(JobNode rootNode) {
        if (rootNode.children.isEmpty()) {
            return List.of(rootNode);
        }

        List<JobNode> leafNodes = new ArrayList<>();
        for (JobNode child : rootNode.children) {
            if (child.children.isEmpty()) {
                leafNodes.add(child);
                continue;
            }
            leafNodes.addAll(findLeafNodes(child));
        }

        return leafNodes;
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
    public static Optional<JobNode> of(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return Optional.empty();
        }
        List<Job> nonNullJobs = jobs.stream()
                .filter(Objects::nonNull)
                .toList();
        Job rootJob = nonNullJobs.stream()
                .filter(job -> job.getParentJobId() == null)
                .findFirst()
                .orElseThrow();
        JobNode rootNode = new JobNode(rootJob);
        List<Job> orphanJobs = new ArrayList<>(nonNullJobs.size());

        for (Job job : nonNullJobs) {
            JobNode parent = findParentById(rootNode, job.getParentJobId()).orElse(null);
            if (parent == null) {
                orphanJobs.add(job);
                continue;
            }

            JobNode newNode = new JobNode(job);
            parent.children.add(newNode);
            if (orphanJobs.isEmpty()) {
                continue;
            }

            checkOrphans(newNode, orphanJobs);
        }

        if (rootNode.size() != nonNullJobs.size()) {
            throw new RuntimeException("Could not fully form a JobNode, got difference of: " +
                    (nonNullJobs.size() - rootNode.size())
            );
        }

        return Optional.of(rootNode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobNode jobNode = (JobNode) o;
        return Objects.equals(job, jobNode.job) && Objects.equals(children, jobNode.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(job, children);
    }

    @Override
    public String toString() {
        return "JobNode{" +
                "job=" + job +
                ", children=" + children +
                '}';
    }
}
