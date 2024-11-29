package com.doppelganger113.commandrunner.batching.job.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobWithDependenciesTest {

    private static JobWithDependencies createJobWithDeps(
            Long id, Long parentId, Long refId, List<JobWithDependencies> deps
    ) {
        return new JobWithDependencies(
                id,
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                parentId,
                deps,
                refId,
                null
        );
    }

    @Test
    void test_findNonRefLeafDependencies_whenNoRefs() {
        //
        // 1 ->
        //  2 ->
        //      4 -> null
        //      5 ->
        //          6 -> null
        //  3 ->
        //      7 -> null
        //
        // Leaf nodes should be 4, 6, 7
        //
        JobWithDependencies gggChild = createJobWithDeps(6L, 5L, null, List.of());
        JobWithDependencies firstChildFirstGrandchild = createJobWithDeps(4L, 2L, null, List.of());
        JobWithDependencies firstChildSecondGrandchild = createJobWithDeps(5L, 2L, null, List.of(gggChild));
        JobWithDependencies firstChild = createJobWithDeps(2L, 1L, null, List.of(
                firstChildFirstGrandchild, firstChildSecondGrandchild
        ));
        JobWithDependencies secondChild = createJobWithDeps(3L, 1L, null,
                List.of(createJobWithDeps(7L, 3L, null, List.of()))
        );
        JobWithDependencies jobWithDependencies = createJobWithDeps(
                1L, null, null, List.of(firstChild, secondChild)
        );

        List<JobWithDependencies> leafJobs = jobWithDependencies.findNonRefLeafDependencies();
        assertEquals(3, leafJobs.size());
        long[] leafIds = leafJobs.stream().mapToLong(JobWithDependencies::id).toArray();
        assertArrayEquals(new long[]{4, 6, 7}, leafIds);
    }

    @Test
    void test_findNonRefLeafDependencies_whenOneRootWithoutDeps() {
        List<JobWithDependencies> leafJobs = createJobWithDeps(1L, null, null, List.of())
                .findNonRefLeafDependencies();
        assertEquals(1, leafJobs.size());
        long[] leafIds = leafJobs.stream().mapToLong(JobWithDependencies::id).toArray();
        assertArrayEquals(new long[]{1}, leafIds);
    }

    @Test
    void test_findNonRefLeafDependencies_whenRefs() {
        //
        // 1 ->
        //  2 ->
        //      4 -> null
        //      5 ->
        //          6 -> ref
        //  3 ->
        //      7 -> ref
        //          8 -> ref
        //
        // Leaf nodes should be 4, 5, 3
        //
        JobWithDependencies gggChild = createJobWithDeps(6L, 5L, 100L, List.of());
        JobWithDependencies firstChildFirstGrandchild = createJobWithDeps(4L, 2L, null, List.of());
        JobWithDependencies firstChildSecondGrandchild = createJobWithDeps(5L, 2L, null, List.of(gggChild));
        JobWithDependencies firstChild = createJobWithDeps(2L, 1L, null, List.of(
                firstChildFirstGrandchild, firstChildSecondGrandchild
        ));
        JobWithDependencies secondChild = createJobWithDeps(3L, 1L, null,
                List.of(createJobWithDeps(7L, 3L, 101L, List.of(
                        createJobWithDeps(8L, 7L, 102L, List.of())
                )))
        );
        JobWithDependencies jobWithDependencies = createJobWithDeps(
                1L, null, null, List.of(firstChild, secondChild)
        );

        List<JobWithDependencies> leafJobs = jobWithDependencies.findNonRefLeafDependencies();
        assertEquals(3, leafJobs.size());
        long[] leafIds = leafJobs.stream().mapToLong(JobWithDependencies::id).toArray();
        assertArrayEquals(new long[]{4, 5, 3}, leafIds);
    }
}