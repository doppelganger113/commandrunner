package com.doppelganger113.commandrunner.batching.job.dto;

import com.doppelganger113.commandrunner.batching.job.Job;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class JobNodeTest {
    private static Job newJob(Integer id, Integer parentId) {
        Job job = new Job();
        job.setId((long) id);
        if (parentId != null) {
            job.setParentJobId((long) parentId);
        }
        return job;
    }

    // 1 ->
    //      2 ->
    //          4
    //      3 ->
    //          7 ->
    //              9
    //          8
    static final List<Job> jobsSort1 = List.of(
            newJob(2, 1),
            newJob(3, 1),
            newJob(4, 2),
            newJob(10, 2),
            newJob(1, null),
            newJob(7, 3),
            newJob(8, 3),
            newJob(9, 7)
    );

    static final List<Job> jobsSort2 = List.of(
            newJob(4, 2),
            newJob(2, 1),
            newJob(10, 2),
            newJob(3, 1),
            newJob(1, null),
            newJob(7, 3),
            newJob(8, 3),
            newJob(9, 7)
    );

    @Test
    public void testOf() {
        Consumer<List<Job>> assertJobs = (jobs) -> {
            JobNode node = JobNode.of(jobs).orElseThrow();
            assertEquals(1, (long) node.job.getId());
            assertEquals(2, node.children.size());
            assertEquals(2, (long) node.children.get(0).job.getId());
            assertEquals(3, (long) node.children.get(1).job.getId());
            // Check 4
            assertEquals(2, node.children.get(0).children.size());
            assertEquals(4, (long) node.children.get(0).children.get(0).job.getId());
            assertEquals(10, (long) node.children.get(0).children.get(1).job.getId());
            // Check 3
            assertEquals(2, node.children.get(1).children.size());
            // Check 7
            assertEquals(1, node.children.get(1).children.get(0).children.size());
            assertEquals(9, (long) node.children.get(1).children.get(0).children.get(0).job.getId());
            // Check 8
            assertEquals(0, node.children.get(1).children.get(1).children.size());
            assertEquals(8, (long) node.children.get(1).children.get(1).job.getId());
        };

        assertJobs.accept(jobsSort1);
        assertJobs.accept(jobsSort2);
    }

    @Test
    void testOfWhenListEmpty() {
        Optional<JobNode> node = JobNode.of(new ArrayList<>());
        assertTrue(node.isEmpty());
    }

    @Test
    void testFindLeafNodes() {
        JobNode rootNode = JobNode.of(jobsSort1).orElseThrow();
        assertNotNull(rootNode);
        List<JobNode> leafNodes = JobNode.findLeafNodes(rootNode);
        assertEquals(4, leafNodes.size());
        assertEquals((long) leafNodes.get(0).job.getId(), 4);
        assertEquals((long) leafNodes.get(1).job.getId(), 10);
        assertEquals((long) leafNodes.get(2).job.getId(), 9);
        assertEquals((long) leafNodes.get(3).job.getId(), 8);
    }

    @Test
    void size() {
        JobNode rootNode = JobNode.of(jobsSort1).orElseThrow();
        assertNotNull(rootNode);
        Assertions.assertEquals(8, rootNode.size());
    }

    // TODO: more tests for invalid data like all null or mostly null
}