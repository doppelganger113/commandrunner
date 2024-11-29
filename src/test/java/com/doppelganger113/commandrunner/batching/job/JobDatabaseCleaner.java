package com.doppelganger113.commandrunner.batching.job;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

public class JobDatabaseCleaner {
    private final JobRepository jobRepository;

    @PersistenceContext
    EntityManager entityManager;

    public JobDatabaseCleaner(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public void cleanDatabase() {
        jobRepository.deleteOriginalJobs();
        jobRepository.deleteAll();
    }
}
