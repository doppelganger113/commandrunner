package com.doppelganger113.commandrunner;

import com.doppelganger113.commandrunner.batching.job.JobExecutor;
import com.doppelganger113.commandrunner.batching.job.JobRepository;
import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.batching.job.dto.JobUpdate;
import com.doppelganger113.commandrunner.batching.job.processors.JobProcessor;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConfiguration(proxyBeanMethods = false)
class JobsIntegrationTests {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:16"
    ).withReuse(true);

    @LocalServerPort
    private Integer port;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    JobExecutor jobExecutor;

    private static final HashMap<String, Object> DEFAULT_HASH_MAP = new HashMap<>(Map.ofEntries(
            Map.entry("age", 32)
    ));
    private static final String DEFAULT_SHA256 = "9ae4b21c4362bce63da43cb728c63763a4f400b9ae9805b06ae6ecb924dd0f9b";

    private record CustomJobProcessor(
            String name,
            Integer durationMs,
            Throwable throwable,
            LinkedBlockingDeque<Boolean> blockingDeque
    ) implements JobProcessor {

        public static CustomJobProcessor DEFAULT = getBuilder()
                .name("custom_processor")
                .build();
        public static CustomJobProcessor THROWABLE = getBuilder()
                .name("failing_processor")
                .throwable(new RuntimeException("Failed again"))
                .build();
        public static CustomJobProcessor SLOW = getBuilder()
                .durationMs(50)
                .name("slow_processor")
                .build();

        public static CustomJobProcessor SLOW_THROWABLE = CustomJobProcessor.getBuilder()
                .name("slow_failing")
                .durationMs(50)
                .throwable(new RuntimeException("failed later"))
                .build();

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void execute(HashMap<String, Object> arguments) {
            if (durationMs != null && durationMs > 0) {
                try {
                    Thread.sleep(durationMs);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (throwable != null) {
                throw new RuntimeException(throwable);
            }
        }

        @Override
        public void after(HashMap<String, Object> arguments) {
            if (blockingDeque != null) {
                blockingDeque.add(true);
            }
        }

        public void waitForCompletionOrFail() {
            try {
                var result = blockingDeque.pollFirst(1, TimeUnit.SECONDS);
                if (result == null) {
                    throw new RuntimeException("Timed out pooling from dequeue");
                }
                if(!blockingDeque.isEmpty()) {
                    throw new RuntimeException(
                            "Size is bigger, did you expect multiple executions of the same job?"
                    );
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private static class Builder {
            private Integer durationMs;
            private Throwable throwable;
            private String name;

            public Builder durationMs(Integer durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public Builder throwable(Throwable throwable) {
                this.throwable = throwable;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public CustomJobProcessor build() {
                return new CustomJobProcessor(name, durationMs, throwable, new LinkedBlockingDeque<>());
            }
        }

        public static Builder getBuilder() {
            return new Builder();
        }
    }

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
        jobRepository.deleteAll();

        // Drain queues
        CustomJobProcessor.DEFAULT.blockingDeque.clear();
        CustomJobProcessor.SLOW.blockingDeque.clear();
        CustomJobProcessor.THROWABLE.blockingDeque.clear();
        CustomJobProcessor.SLOW_THROWABLE.blockingDeque.clear();
    }

    @Test
    void givenNewJobCreation_whenThereAreNotAny_thenReturnListOfOneNewJob() {
        jobExecutor.addJobProcessor(CustomJobProcessor.DEFAULT);

        given()
                .body(new JobExecutionOptions(CustomJobProcessor.DEFAULT.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.id", not(notANumber()),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("READY"),
                        "job.arguments.age", equalTo(32)
                );

        CustomJobProcessor.DEFAULT.waitForCompletionOrFail();

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs")
                .then()
                .statusCode(200)
                .body(
                        ".", hasSize(1),
                        "[0].state", equalTo("COMPLETED"),
                        "[0].createdAt", not(emptyString()),
                        "[0].startedAt", not(emptyString()),
                        "[0].completedAt", not(emptyString()),
                        "[0].durationMs", not(notANumber()),
                        "[0].arguments.age", equalTo(32)
                );
    }

    @Test
    void givenNewJobCreation_whenThereAreNotAny_thenReturnNewJob() {
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessor.DEFAULT.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.id", not(notANumber()),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("READY"),
                        "job.arguments.age", equalTo(32)
                )
                .extract().path("job.id");


        CustomJobProcessor.DEFAULT.waitForCompletionOrFail();

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs/" + jobId)
                .then()
                .statusCode(200)
                .body(
                        "state", equalTo("COMPLETED"),
                        "createdAt", not(emptyString()),
                        "startedAt", not(emptyString()),
                        "completedAt", not(emptyString()),
                        "durationMs", not(notANumber()),
                        "arguments.age", equalTo(32)
                );
    }

    @Test
    void givenNewJobCreation_whenJobExecutorDoesNotExist_thenFail() {
        given()
                .body(new JobExecutionOptions("unknown", null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(400);
    }

    @Test
    void givenNewJobCreation_whenIdenticalJobWasCreatedAndRunning_thenReturnPreviousJob() {
        jobExecutor.addJobProcessor(CustomJobProcessor.SLOW);

        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.id", not(notANumber()),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("READY"),
                        "job.arguments.age", equalTo(32)
                )
                .extract().path("job.id");

        given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("RUNNING"),
                        "job.id", equalTo(jobId),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("RUNNING"),
                        "job.arguments.age", equalTo(32)
                );

        CustomJobProcessor.SLOW.waitForCompletionOrFail();

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs")
                .then()
                .statusCode(200)
                .body(
                        ".", hasSize(1),
                        "[0].id", equalTo(jobId),
                        "[0].state", equalTo("COMPLETED"),
                        "[0].createdAt", not(emptyString()),
                        "[0].startedAt", not(emptyString()),
                        "[0].completedAt", not(emptyString()),
                        "[0].durationMs", not(notANumber()),
                        "[0].arguments.age", equalTo(32)
                );
    }

    @Test
    void givenNewJobCreation_whenJobWithSameNameButDiffArgsIsRunning_thenReturnStatus() {
        jobExecutor.addJobProcessor(CustomJobProcessor.SLOW);

        // Create the
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.name", equalTo(CustomJobProcessor.SLOW.name()),
                        "job.id", not(notANumber()),
                        "job.arguments", equalTo(null),
                        "job.argumentsHash", equalTo(""),
                        "job.state", equalTo("READY")
                )
                .extract().path("job.id");

        Integer jobId2 = given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("RUNNING"),
                        "job.name", equalTo(CustomJobProcessor.SLOW.name()),
                        "job.id", not(notANumber()),
                        "job.arguments", equalTo(null),
                        "job.argumentsHash", equalTo(""),
                        "job.state", equalTo("RUNNING")
                )
                .extract().path("job.id");

        Assertions.assertEquals(jobId, jobId2);
    }

    @Test
    void givenNewJobCreation_whenIdenticalJobWasCreatedAndCompleted_thenReturnPreviousJob() {
        jobExecutor.addJobProcessor(CustomJobProcessor.DEFAULT);

        HashMap<String, Object> params = new HashMap<>();
        params.put("age", 32);

        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessor.DEFAULT.name(), params))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.id", not(notANumber()),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("READY"),
                        "job.arguments.age", equalTo(32)
                )
                .extract().path("job.id");

        CustomJobProcessor.DEFAULT.waitForCompletionOrFail();

        given()
                .body(new JobExecutionOptions(CustomJobProcessor.DEFAULT.name(), params))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("COMPLETED"),
                        "job.id", equalTo(jobId),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("COMPLETED"),
                        "job.arguments.age", equalTo(32)
                );

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs")
                .then()
                .statusCode(200)
                .body(
                        ".", hasSize(1),
                        "[0].id", equalTo(jobId),
                        "[0].state", equalTo("COMPLETED"),
                        "[0].createdAt", not(emptyString()),
                        "[0].startedAt", not(emptyString()),
                        "[0].completedAt", not(emptyString()),
                        "[0].durationMs", not(notANumber()),
                        "[0].arguments.age", equalTo(32)
                );
    }

    @Test
    void givenStoppingJob_whenThatJobIsRunning_thenReturnStoppingStateAndStoppedState() {
        jobExecutor.addJobProcessor(CustomJobProcessor.SLOW);

        // Create a new job
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.id", not(notANumber()),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("READY"),
                        "job.arguments.age", equalTo(32)
                )
                .extract().path("job.id");

        // Stop the job
        given()
                .body(new JobUpdate(true))
                .contentType(ContentType.JSON)
                .when()
                .patch("/jobs/" + jobId)
                .then()
                .statusCode(204);

        // Verify if the job is in STOPPING state
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs/" + jobId)
                .then()
                .statusCode(200)
                .body("state", equalTo("STOPPING"));

        CustomJobProcessor.SLOW.waitForCompletionOrFail();

        // Verify if the job was stopped
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs/" + jobId)
                .then()
                .statusCode(200)
                .body(
                        "id", equalTo(jobId),
                        "state", equalTo("STOPPED"),
                        "createdAt", not(emptyString()),
                        "startedAt", not(emptyString()),
                        "completedAt", not(emptyString()),
                        "durationMs", not(notANumber()),
                        "arguments.age", equalTo(32)
                );
    }

    @Test
    void givenNewJob_whenStoppingItWhileRunningButItFails_thenReturnFailedStateWithError() {
        jobExecutor.addJobProcessor(CustomJobProcessor.SLOW_THROWABLE);

        // Create a new job
        Integer jobId = given()
                .body(new JobExecutionOptions("slow_failing", DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.id", not(notANumber()),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("READY"),
                        "job.arguments.age", equalTo(32)
                )
                .extract().path("job.id");

        // Stop the job
        given()
                .body(new JobUpdate(true))
                .contentType(ContentType.JSON)
                .when()
                .patch("/jobs/" + jobId)
                .then()
                .statusCode(204);

        // Verify if the job is in STOPPING state
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs/" + jobId)
                .then()
                .statusCode(200)
                .body("state", equalTo("STOPPING"));

        CustomJobProcessor.SLOW_THROWABLE.waitForCompletionOrFail();

        // Verify if the job was failed
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs/" + jobId)
                .then()
                .statusCode(200)
                .body(
                        "id", equalTo(jobId),
                        "state", equalTo("FAILED"),
                        "createdAt", not(emptyString()),
                        "startedAt", not(emptyString()),
                        "completedAt", not(emptyString()),
                        "durationMs", not(notANumber()),
                        "arguments.age", equalTo(32),
                        "error", containsString("failed later")
                );
    }

    @Test
    void givenNewJob_whenSameJobIsBeingStopped_thenReturnRunningJobWithStateStopping() {
        jobExecutor.addJobProcessor(CustomJobProcessor.SLOW);

        // Create a new job
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.id", not(notANumber()),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("READY"),
                        "job.arguments.age", equalTo(32)
                )
                .extract().path("job.id");

        // Stop the job
        given()
                .body(new JobUpdate(true))
                .contentType(ContentType.JSON)
                .when()
                .patch("/jobs/" + jobId)
                .then()
                .statusCode(204);

        // Try to create the same job again
        given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("RUNNING"),
                        "job.id", equalTo(jobId),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("STOPPING"),
                        "job.arguments.age", equalTo(32)
                );

        CustomJobProcessor.SLOW.waitForCompletionOrFail();

        // Verify if the job was failed
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs/" + jobId)
                .then()
                .statusCode(200)
                .body(
                        "id", equalTo(jobId),
                        "state", equalTo("STOPPED"),
                        "createdAt", not(emptyString()),
                        "startedAt", not(emptyString()),
                        "completedAt", not(emptyString()),
                        "durationMs", not(notANumber()),
                        "arguments.age", equalTo(32),
                        "error", nullValue()
                );
    }

    @Test
    void givenNewJob_whenSameJobIsStopped_thenReturnStoppedJob() {
        jobExecutor.addJobProcessor(CustomJobProcessor.SLOW);

        // Create a new job
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.id", not(notANumber()),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("READY"),
                        "job.arguments.age", equalTo(32)
                )
                .extract().path("job.id");

        // Stop the job
        given()
                .body(new JobUpdate(true))
                .contentType(ContentType.JSON)
                .when()
                .patch("/jobs/" + jobId)
                .then()
                .statusCode(204);

        CustomJobProcessor.SLOW.waitForCompletionOrFail();

        // Try to create the same job again
        given()
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("COMPLETED"),
                        "job.id", equalTo(jobId),
                        "job.argumentsHash", equalTo(DEFAULT_SHA256),
                        "job.state", equalTo("STOPPED"),
                        "job.arguments.age", equalTo(32)
                );
    }

    @Test
    void givenCreateNewJob_whenJobRunnerFails_thenReturnFailedJob() {
        jobExecutor.addJobProcessor(CustomJobProcessor.THROWABLE);

        // Create a failing job
        given()
                .body(new JobExecutionOptions(CustomJobProcessor.THROWABLE.name(), null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.name", equalTo(CustomJobProcessor.THROWABLE.name()),
                        "job.id", not(notANumber()),
                        "job.arguments", equalTo(null),
                        "job.argumentsHash", equalTo(""),
                        "job.state", equalTo("READY")
                );

        CustomJobProcessor.THROWABLE.waitForCompletionOrFail();

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs")
                .then()
                .statusCode(200)
                .body(
                        ".", hasSize(1),
                        "[0].state", equalTo("FAILED"),
                        "[0].createdAt", not(emptyString()),
                        "[0].startedAt", not(emptyString()),
                        "[0].durationMs", not(notANumber()),
                        "[0].completedAt", not(emptyString()),
                        "[0].arguments", equalTo(null),
                        "[0].argumentsHash", equalTo(""),
                        "[0].error", containsString("Failed again")
                );
    }

    // TODO: queuing jobs?
}
