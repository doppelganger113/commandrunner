package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.batching.job.dto.JobUpdate;
import com.doppelganger113.commandrunner.batching.job.processors.JobProcessor;
import com.doppelganger113.commandrunner.batching.job.testhelp.JobsIntegrationBootstrap;
import com.doppelganger113.commandrunner.batching.job.testhelp.db.JobDatabaseCleaner;
import com.doppelganger113.commandrunner.batching.job.testhelp.db.JobTestConfiguration;
import com.doppelganger113.commandrunner.batching.job.testhelp.processors.CustomJobProcessorFactory;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class JobsIntegrationTests extends JobsIntegrationBootstrap {

    @Test
    void givenNewJobCreation_whenThereAreNotAny_thenReturnListOfOneNewJob() {
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.DEFAULT);

        given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.DEFAULT.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", not(notANumber()),
                        "data.uniqueJobId", equalTo(DEFAULT_SHA256),
                        "data.state", equalTo("READY"),
                        "data.arguments.age", equalTo(32)
                );

        CustomJobProcessorFactory.DEFAULT.waitForCompletionOrFail();

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
                .body(new JobExecutionOptions(CustomJobProcessorFactory.DEFAULT.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", not(notANumber()),
                        "data.uniqueJobId", equalTo(DEFAULT_SHA256),
                        "data.state", equalTo("READY"),
                        "data.arguments.age", equalTo(32)
                )
                .extract().path("data.id");


        CustomJobProcessorFactory.DEFAULT.waitForCompletionOrFail();

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
    void givenNewJobCreation_whenIdenticalJobWasCreatedAndRunning_thenReturnReferencedJob() {
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.SLOW);

        // We create a slow job
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", not(notANumber()),
                        "data.uniqueJobId", equalTo(DEFAULT_SHA256),
                        "data.state", equalTo("READY"),
                        "data.arguments.age", equalTo(32)
                )
                .extract().path("data.id");

        // We try to run again the same slow job and get the reference to it
        given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", notNullValue(),
                        "data.referenceJobId", equalTo(jobId),
                        "data.uniqueJobId", containsString(DEFAULT_SHA256 + "_"),
                        "data.state", equalTo("REFERENCED"),
                        "data.arguments.age", equalTo(32)
                );

        CustomJobProcessorFactory.SLOW.waitForCompletionOrFail();

        // After waiting for completion we check if the job is done
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs")
                .then()
                .statusCode(200)
                .body(
                        ".", hasSize(2),
                        "[1].id", equalTo(jobId),
                        "[1].state", equalTo("COMPLETED"),
                        "[1].createdAt", not(emptyString()),
                        "[1].startedAt", not(emptyString()),
                        "[1].completedAt", not(emptyString()),
                        "[1].durationMs", not(notANumber()),
                        "[1].arguments.age", equalTo(32)
                );
    }

    @Test
    void givenNewJobCreation_whenJobWithSameNameButDiffArgsIsRunning_thenReturnStatus() {
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.SLOW);

        // Create the slow job
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), null, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.name", equalTo(CustomJobProcessorFactory.SLOW.name()),
                        "data.id", not(notANumber()),
                        "data.arguments", equalTo(null),
                        "data.uniqueJobId", equalTo(""),
                        "data.state", equalTo("READY")
                )
                .extract().path("data.id");

        /*
         Create slow job again, but with different arguments, but should not be COMPLETED as it cannot run
         immediately
        */
        Integer jobId2 = given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.name", equalTo(CustomJobProcessorFactory.SLOW.name()),
                        "data.id", not(notANumber()),
                        "data.arguments.age", equalTo(32),
                        "data.uniqueJobId", equalTo("9ae4b21c4362bce63da43cb728c63763a4f400b9ae9805b06ae6ecb924dd0f9b"),
                        "data.state", equalTo("READY")
                )
                .extract().path("data.id");

        Assertions.assertNotEquals(jobId, jobId2);
    }

    @Test
    void givenNewJobCreation_whenIdenticalJobWasCreatedAndCompleted_thenReturnPreviousJob() {
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.DEFAULT);

        HashMap<String, Object> params = new HashMap<>();
        params.put("age", 32);

        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.DEFAULT.name(), params, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", not(notANumber()),
                        "data.uniqueJobId", equalTo(DEFAULT_SHA256),
                        "data.state", equalTo("READY"),
                        "data.arguments.age", equalTo(32)
                )
                .extract().path("data.id");

        CustomJobProcessorFactory.DEFAULT.waitForCompletionOrFail();

        given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.DEFAULT.name(), params, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", notNullValue(),
                        "data.uniqueJobId", containsString(DEFAULT_SHA256 + "_"),
                        "data.state", equalTo("REFERENCED"),
                        "data.arguments.age", equalTo(32),
                        "data.referenceJobId", equalTo(jobId),
                        "data.error", nullValue(),
                        "data.dependencies", hasSize(0)
                );

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs")
                .then()
                .statusCode(200)
                .body(
                        ".", hasSize(2),
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
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.SLOW);

        // Create a new slow job
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", not(notANumber()),
                        "data.uniqueJobId", equalTo(DEFAULT_SHA256),
                        "data.state", equalTo("READY"),
                        "data.arguments.age", equalTo(32)
                )
                .extract().path("data.id");

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

        CustomJobProcessorFactory.SLOW.waitForCompletionOrFail();

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
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.SLOW_THROWABLE);

        // Create a new job
        Integer jobId = given()
                .body(new JobExecutionOptions("slow_failing", DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", not(notANumber()),
                        "data.uniqueJobId", equalTo(DEFAULT_SHA256),
                        "data.state", equalTo("READY"),
                        "data.arguments.age", equalTo(32)
                )
                .extract().path("data.id");

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

        CustomJobProcessorFactory.SLOW_THROWABLE.waitForCompletionOrFail();

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
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.SLOW);

        // Create a new job
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", not(notANumber()),
                        "data.uniqueJobId", equalTo(DEFAULT_SHA256),
                        "data.state", equalTo("READY"),
                        "data.arguments.age", equalTo(32)
                )
                .extract().path("data.id");

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
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", notNullValue(),
                        "data.referenceJobId", equalTo(jobId),
                        "data.uniqueJobId", containsString(DEFAULT_SHA256 + "_"),
                        "data.state", equalTo("REFERENCED"),
                        "data.arguments.age", equalTo(32)
                );

        CustomJobProcessorFactory.SLOW.waitForCompletionOrFail();

        // Verify if the job has failed
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
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.SLOW);

        // Create a new job
        Integer jobId = given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", not(notANumber()),
                        "data.uniqueJobId", equalTo(DEFAULT_SHA256),
                        "data.state", equalTo("READY"),
                        "data.arguments.age", equalTo(32)
                )
                .extract().path("data.id");

        // Stop the job
        given()
                .body(new JobUpdate(true))
                .contentType(ContentType.JSON)
                .when()
                .patch("/jobs/" + jobId)
                .then()
                .statusCode(204);

        CustomJobProcessorFactory.SLOW.waitForCompletionOrFail();

        // Try to create the same job again
        given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.SLOW.name(), DEFAULT_HASH_MAP, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.id", notNullValue(),
                        "data.referenceJobId", equalTo(jobId),
                        "data.uniqueJobId", containsString(DEFAULT_SHA256 + "_"),
                        "data.state", equalTo("REFERENCED"),
                        "data.arguments.age", equalTo(32)
                );
    }

    @Test
    void givenCreateNewJob_whenJobRunnerFails_thenReturnFailedJob() {
        jobExecutor.addJobProcessor(CustomJobProcessorFactory.THROWABLE);

        // Create a failing job
        var res = given()
                .body(new JobExecutionOptions(CustomJobProcessorFactory.THROWABLE.name(), null, null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.name", equalTo(CustomJobProcessorFactory.THROWABLE.name()),
                        "data.id", not(notANumber()),
                        "data.arguments", equalTo(null),
                        "data.uniqueJobId", equalTo(""),
                        "data.state", equalTo("READY")
                );

        CustomJobProcessorFactory.THROWABLE.waitForCompletionOrFail();

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
                        "[0].uniqueJobId", equalTo(""),
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
                        "error", containsString("Duplicate job execution options")
                );
    }

    @Test
    void givenJobWithChildJobs_whenThereAreTooMany_thenReturnBadRequest() {
        // TODO: implement a test and functionality for this
        Assertions.assertTrue(false);
    }
}
