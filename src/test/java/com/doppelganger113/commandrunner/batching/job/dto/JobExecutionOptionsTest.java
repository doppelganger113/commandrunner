package com.doppelganger113.commandrunner.batching.job.dto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class JobExecutionOptionsTest {

    @Test
    void hasDuplicates_givenJobWithChildren_whenDuplicateExists_thenReturnTrue() {

        JobExecutionOptions jobExecutionOptions2 = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "John")
                )),
                null);

        JobExecutionOptions jobExecutionOption3 = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", 23)
                )),
                null);

        JobExecutionOptions jobExecutionOption4 = new JobExecutionOptions(
                "another-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "John")
                )),
                List.of(jobExecutionOptions2, jobExecutionOption3)
        );

        JobExecutionOptions jobExecutionOptions = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "John")
                )),
                List.of(jobExecutionOption4));

        Assertions.assertTrue(jobExecutionOptions.hasDuplicate());
    }

    @Test
    void hasDuplicates_givenJobWithChildren_whenNoDuplicate_thenReturnFalse() {
        JobExecutionOptions jobExecutionOptions2 = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("age", 33)
                )),
                null);

        JobExecutionOptions jobExecutionOption3 = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", 23)
                )),
                null);

        JobExecutionOptions jobExecutionOption4 = new JobExecutionOptions(
                "another-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "John")
                )),
                List.of(jobExecutionOptions2, jobExecutionOption3)
        );

        JobExecutionOptions jobExecutionOptions = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "John")
                )),
                List.of(jobExecutionOption4));

        Assertions.assertFalse(jobExecutionOptions.hasDuplicate());
    }

    @Test
    void flatten_shouldReturnListOfJobExecutionOptions() {
        JobExecutionOptions jobExecutionOptions2 = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("age", 33)
                )),
                null);

        JobExecutionOptions jobExecutionOption3 = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", 23)
                )),
                null);

        JobExecutionOptions jobExecutionOption4 = new JobExecutionOptions(
                "another-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "John")
                )),
                List.of(jobExecutionOptions2, jobExecutionOption3)
        );

        JobExecutionOptions jobExecutionOptions = new JobExecutionOptions(
                "my-job",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "John")
                )),
                List.of(jobExecutionOption4));

        Assertions.assertEquals(4, jobExecutionOptions.flatten().size());
    }
}