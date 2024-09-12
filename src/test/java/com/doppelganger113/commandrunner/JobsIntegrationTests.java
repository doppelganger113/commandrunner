package com.doppelganger113.commandrunner;

import com.doppelganger113.commandrunner.batching.job.Job;
import com.doppelganger113.commandrunner.batching.job.JobExecutor;
import com.doppelganger113.commandrunner.batching.job.JobRepository;
import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.batching.job.dto.JobUpdate;
import com.doppelganger113.commandrunner.batching.job.processors.JobProcessor;
import groovy.util.MapEntry;
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

import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConfiguration(proxyBeanMethods = false)
class JobsIntegrationTests {

    // This number is taken as fair enough time for CI/CD when a slow machine is executing tests
    private static final int JOB_SLOWDOWN_MLS = 200;

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
                .durationMs(JOB_SLOWDOWN_MLS)
                .name("slow_processor")
                .build();

        public static CustomJobProcessor SLOW_THROWABLE = CustomJobProcessor.getBuilder()
                .name("slow_failing")
                .durationMs(JOB_SLOWDOWN_MLS)
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
                if (!blockingDeque.isEmpty()) {
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
                .body(new JobExecutionOptions(CustomJobProcessor.DEFAULT.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.DEFAULT.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions("unknown", null, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), null, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.DEFAULT.name(), params, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.DEFAULT.name(), params, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions("slow_failing", DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.SLOW.name(), DEFAULT_HASH_MAP, null))
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
                .body(new JobExecutionOptions(CustomJobProcessor.THROWABLE.name(), null, null))
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

    // TODO: add a test where it validates that the job cannot have 2 jobs of the same hash

    @Test
    void givenNewJobWithChildren_whenCreating_thenReturnCreatedJobWithChildren() {

        // TODO: move into repository integration test
        Job fatherJob = new Job("my-job");

        Job firstSonJob = new Job("my-job");
        firstSonJob.setChildren(List.of(new Job("my-job")));

        fatherJob.setChildren(List.of(new Job("my-job"), firstSonJob));

        jobRepository.save(fatherJob);

        Job foundFatherJob = jobRepository.findById(fatherJob.getId()).orElseThrow();
        List<Job> fatherChildren = jobRepository.findJobsByParentJobId(foundFatherJob.getId());
        Assertions.assertEquals(2, fatherChildren.size());

        List<Job> firstSonChild = jobRepository.findJobsByParentJobId(firstSonJob.getId());
        Assertions.assertEquals(1, firstSonChild.size());
    }

    @Test
    void givenJobWithChildJobs_whenThereAreDuplicate_thenReturnBadRequest() {
        JobExecutionOptions grandChild3 = new JobExecutionOptions("my-job", null, null);

        JobExecutionOptions child2 = new JobExecutionOptions("my-job", null, List.of(grandChild3));
        JobExecutionOptions child1 = new JobExecutionOptions("my-job", null, null);

        JobExecutionOptions main = new JobExecutionOptions("my-job", null, List.of(child1, child2));

        given()
                .body(main)
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(409)
                .body(
                        "error", equalTo("Conflict"),
                        "status", equalTo(409),
                        "path", equalTo("/jobs")
                );
    }

    @Test
    void givenJobWithChildJobsExistsInDb_whenWeQueryByJobId_thenReturnsJobWithItsChildJobs() {
        jobExecutor.addJobProcessor(
                CustomJobProcessor.getBuilder()
                        .name("my-job")
                        .build()
        );
        jobExecutor.addJobProcessor(
                CustomJobProcessor.getBuilder()
                        .name("my-job_1")
                        .build()
        );
        jobExecutor.addJobProcessor(
                CustomJobProcessor.getBuilder()
                        .name("my-job_2")
                        .build()
        );
        jobExecutor.addJobProcessor(
                CustomJobProcessor.getBuilder()
                        .name("my-job_3")
                        .build()
        );
        jobExecutor.addJobProcessor(
                CustomJobProcessor.getBuilder()
                        .name("my-job_4")
                        .build()
        );

        JobExecutionOptions grandChild3 = new JobExecutionOptions(
                "my-job_4",
                new HashMap<>(Map.ofEntries(Map.entry("name", "John"))),
                null
        );
        JobExecutionOptions grandChild2 = new JobExecutionOptions("my-job_4", null, null);
        JobExecutionOptions grandChild1 = new JobExecutionOptions("my-job_3", null, null);

        JobExecutionOptions child2 = new JobExecutionOptions("my-job_2", null, List.of(grandChild3));
        JobExecutionOptions child1 = new JobExecutionOptions("my-job_1", null, List.of(grandChild1, grandChild2));

        JobExecutionOptions main = new JobExecutionOptions("my-job", null, List.of(child1, child2));

        // Request here:
        var res = given()
                .body(main)
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs");

        res.getBody().prettyPrint();

        res.then()
                .statusCode(200)
                .body(
                        "description", equalTo("CREATED"),
                        "job.name", equalTo(CustomJobProcessor.THROWABLE.name()),
                        "job.id", not(notANumber()),
                        "job.arguments", equalTo(null),
                        "job.argumentsHash", equalTo(""),
                        "job.state", equalTo("READY")
                );


        // TODO: finish this check as we can now save and want to verify if the state in the db is correct
        // TODO: also see if it's possible to add child node parent ids as they are missing in the response above
//        var response = given()
//                .contentType(ContentType.JSON)
//                .when()
//                .get("/jobs/" + job1.getId() + "/dependencies");
//
//        response.getBody().prettyPrint();
//
//        response.then()
//                .statusCode(200)
//                .body(
//                        "children", hasSize(1),
//                        "state", equalTo("COMPLETED"),
//                        "createdAt", not(emptyString()),
//                        "startedAt", not(emptyString()),
//                        "durationMs", not(notANumber()),
//                        "completedAt", not(emptyString()),
//                        "arguments", equalTo(null),
//                        "argumentsHash", equalTo(""),
//                        "error", equalTo(null)
//                );
//
//        // TODO: create an API for creating batch of jobs
//
//        List<Job> jobs = jobRepository.findJobByIdAndItsDependencies(job1.getId())
//                .stream()
//                .sorted(Comparator.comparingLong(Job::getId))
//                .toList();
//        Assertions.assertEquals(8, jobs.size());
//
//        Assertions.assertEquals(
//                jobs.stream()
//                        .map(Job::getId)
//                        .toList(),
//                List.of(
//                        job1.getId(),
//                        job2.getId(),
//                        job3.getId(),
//                        job4.getId(),
//                        job7.getId(),
//                        job8.getId(),
//                        job9.getId(),
//                        job10.getId()
//                )
//        );
    }
}
