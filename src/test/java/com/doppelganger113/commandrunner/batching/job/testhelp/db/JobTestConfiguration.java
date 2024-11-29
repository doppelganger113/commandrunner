package com.doppelganger113.commandrunner.batching.job.testhelp.db;

import com.doppelganger113.commandrunner.batching.job.JobRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class JobTestConfiguration {

    @Bean
    public JobDatabaseCleaner jobDatabaseCleaner(JobRepository jobRepository) {
        return new JobDatabaseCleaner(jobRepository);
    }
}
