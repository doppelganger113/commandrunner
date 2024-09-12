package com.doppelganger113.commandrunner.batching.job;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    @Test
    void replaceChildWith_givenJobTree_whenThereExistsIdenticalJob_thenReplaceIt() {
        Job kid1 = new Job();
        kid1.setName("my-job_1");
        kid1.setArguments(new HashMap<>(Map.ofEntries(
                Map.entry("name", "John"),
                Map.entry("age", 33)
        )));

        Job kid2 = new Job();
        kid2.setName("my-job_2");
        kid2.setArguments(new HashMap<>(Map.ofEntries(
                Map.entry("name", "John"),
                Map.entry("age", 33)
        )));

        Job kid3 = new Job();
        kid3.setName("my-job_3");
        kid3.setArguments(new HashMap<>(Map.ofEntries(
                Map.entry("name", "John"),
                Map.entry("age", 33)
        )));
        kid3.setChildren(List.of(kid1, kid2));

        Job job = new Job();
        job.setName("my-job");
        job.setArguments(new HashMap<>(Map.ofEntries(
                Map.entry("name", "John"),
                Map.entry("age", 33)
        )));
        job.setChildren(List.of(kid3));

        List<Job> flatJobs = job.flatten();
        assertEquals(4, flatJobs.size());

        Job duplicateJob = new Job();
        duplicateJob.setId(2L);
        duplicateJob.setName("my-job_2");
        duplicateJob.setArguments(new HashMap<>(Map.ofEntries(
                Map.entry("name", "John"),
                Map.entry("age", 33)
        )));

        boolean wasReplaced = job.replaceChildWith(duplicateJob);
        assertTrue(wasReplaced);

        Optional<Job> replacedChildJob = job.findChildJobByNameAndArgs("my-job_2", new HashMap<>(Map.ofEntries(
                Map.entry("name", "John"),
                Map.entry("age", 33)
        )));
        assertEquals("my-job_2", replacedChildJob.get().getName());
        assertEquals("John", replacedChildJob.get().getArguments().get("name"));
        assertEquals(33, replacedChildJob.get().getArguments().get("age"));
        assertEquals(2L, replacedChildJob.get().getId());
    }
}