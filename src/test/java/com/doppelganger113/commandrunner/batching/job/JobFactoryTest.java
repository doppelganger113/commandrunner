package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.hash.ShaHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
class JobFactoryTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        JobFactory jobFactory() {
            return new JobFactory(new ShaHash());
        }
    }

    @Autowired
    JobFactory jobFactory;

    @Test
    void from_givenDefaultJobExecutionOptionsTree_whenThereAreChildren_thenReturnSameStructure() {
        JobExecutionOptions jobExecutionOptions1 = new JobExecutionOptions(
                "my-job_1",
                null,
                null
        );
        JobExecutionOptions jobExecutionOptions2 = new JobExecutionOptions(
                "my-job_2",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "Ana")
                )),
                null
        );
        JobExecutionOptions jobExecutionOptions3 = new JobExecutionOptions(
                "my-job_3",
                null,
                List.of(jobExecutionOptions1, jobExecutionOptions2)
        );
        JobExecutionOptions jobExecutionOptions4 = new JobExecutionOptions(
                "my-job_4",
                new HashMap<>(Map.ofEntries(
                        Map.entry("name", "John"),
                        Map.entry("age", 33)
                )),
                null
        );
        JobExecutionOptions jobExecutionOptions5 = new JobExecutionOptions(
                "my-job_5",
                null,
                List.of(jobExecutionOptions3, jobExecutionOptions4)
        );

        Job job = jobFactory.from(jobExecutionOptions5);
        assertEquals("", job.getArgumentsHash());
        assertEquals(job.getChildren().size(), 2);
        Job job4 = job.getChildren().stream().filter(j -> j.getName().equals("my-job_4")).findFirst().get();
        assertEquals(
                "05870e47f19dd43433157e393bb38238433358d43ea84a61a7a384df76b3d7a5",
                job4.getArgumentsHash()
        );
    }
}