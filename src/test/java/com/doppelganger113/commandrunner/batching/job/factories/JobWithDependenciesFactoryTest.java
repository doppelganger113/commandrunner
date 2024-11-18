package com.doppelganger113.commandrunner.batching.job.factories;

import com.doppelganger113.commandrunner.batching.job.Job;
import com.doppelganger113.commandrunner.batching.job.dto.JobWithDependencies;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class JobWithDependenciesFactoryTest {
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
    //          10
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

    // 1 ->
    //      2 ->
    //          4
    //          10
    //      3 ->
    //          7 ->
    //              9
    //          8
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
    public void testFrom() {
        Consumer<List<Job>> assertJobs = (jobs) -> {
            JobWithDependencies node = JobWithDependenciesFactory.fromJobs(jobs).orElseThrow();
            assertEquals(1, (long) node.id());
            assertEquals(2, node.dependencies().size());

            assertArrayEquals(
                    new long[]{2, 3},
                    node.dependencies().stream().mapToLong(JobWithDependencies::id).toArray()
            );

            assertArrayEquals(
                    new long[]{4, 10},
                    node.dependencies().stream()
                            .filter(j -> j.id().equals(2L))
                            .findFirst()
                            .get()
                            .dependencies()
                            .stream()
                            .mapToLong(JobWithDependencies::id)
                            .toArray()
            );

            assertArrayEquals(
                    new long[]{7, 8},
                    node.dependencies().stream()
                            .filter(j -> j.id().equals(3L))
                            .findFirst()
                            .get()
                            .dependencies()
                            .stream()
                            .mapToLong(JobWithDependencies::id)
                            .toArray()
            );

            assertArrayEquals(
                    new long[]{9},
                    node.dependencies().stream()
                            .filter(j -> j.id().equals(3L))
                            .findFirst()
                            .get()
                            .dependencies()
                            .stream()
                            .filter(j -> j.id().equals(7L))
                            .findFirst()
                            .get()
                            .dependencies()
                            .stream()
                            .mapToLong(JobWithDependencies::id)
                            .toArray()
            );

            int eightSize = node.dependencies().stream()
                    .filter(j -> j.id().equals(3L))
                    .findFirst()
                    .get()
                    .dependencies()
                    .stream()
                    .filter(j -> j.id().equals(8L))
                    .findFirst()
                    .get()
                    .dependencies()
                            .size();

            assertEquals(0, eightSize);
        };

        assertJobs.accept(jobsSort1);
        assertJobs.accept(jobsSort2);
    }

    @Test
    void testOfWhenListEmpty() {
        Optional<JobWithDependencies> node = JobWithDependenciesFactory.fromJobs(new ArrayList<>());
        assertTrue(node.isEmpty());
    }

    @Test
    void testFindLeafNodes() {
        JobWithDependencies rootNode = JobWithDependenciesFactory.fromJobs(jobsSort1).orElseThrow();
        assertNotNull(rootNode);
        List<JobWithDependencies> leafNodes = JobWithDependenciesFactory.findLeafNodes(rootNode);
        assertEquals(4, leafNodes.size());
        long[] leafIds = leafNodes.stream().mapToLong(JobWithDependencies::id).toArray();
        assertArrayEquals(new long[]{4, 10, 9, 8}, leafIds);
    }

    @Test
    void testFindLeafNodesNotReferences() {
        // TODO: add test or two
    }

    @Test
    void size() {
        JobWithDependencies rootNode = JobWithDependenciesFactory.fromJobs(jobsSort1).orElseThrow();
        assertNotNull(rootNode);
        Assertions.assertEquals(8, rootNode.size());
    }
}