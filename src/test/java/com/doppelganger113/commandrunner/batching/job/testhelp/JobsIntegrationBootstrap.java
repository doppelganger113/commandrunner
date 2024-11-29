package com.doppelganger113.commandrunner.batching.job.testhelp;

import com.doppelganger113.commandrunner.batching.job.ConcurrentJobExecutor;
import com.doppelganger113.commandrunner.batching.job.JobRepository;
import com.doppelganger113.commandrunner.batching.job.testhelp.db.JobDatabaseCleaner;
import com.doppelganger113.commandrunner.batching.job.testhelp.db.JobTestConfiguration;
import com.doppelganger113.commandrunner.batching.job.testhelp.processors.CustomJobProcessorFactory;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConfiguration(proxyBeanMethods = false)
@Import(JobTestConfiguration.class)
public abstract class JobsIntegrationBootstrap {
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:16"
    ).withReuse(true);

    @LocalServerPort
    protected Integer port;

    @Autowired
    protected JobRepository jobRepository;

    @Autowired
    protected ConcurrentJobExecutor jobExecutor;

    @Autowired
    protected JobDatabaseCleaner cleaner;

    protected static final HashMap<String, Object> DEFAULT_HASH_MAP = new HashMap<>(Map.ofEntries(
            Map.entry("age", 32)
    ));
    protected static final String DEFAULT_SHA256 = "9ae4b21c4362bce63da43cb728c63763a4f400b9ae9805b06ae6ecb924dd0f9b";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @BeforeEach
    void beforeEach() {
        RestAssured.baseURI = "http://localhost:" + port;

        cleaner.cleanDatabase();

        // Drain queues
        CustomJobProcessorFactory.DEFAULT.clearQueue();
        CustomJobProcessorFactory.SLOW.clearQueue();
        CustomJobProcessorFactory.THROWABLE.clearQueue();
        CustomJobProcessorFactory.SLOW_THROWABLE.clearQueue();
    }
}
