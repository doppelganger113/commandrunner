package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.batching.job.testhelp.JobsIntegrationBootstrap;
import com.doppelganger113.commandrunner.batching.job.testhelp.db.JobTestConfiguration;
import com.doppelganger113.commandrunner.batching.job.testhelp.processors.CustomJobProcessorFactory;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;

/**
 * Due to number of combinations of tests for job dependencies, it's better to have this as a separate test suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConfiguration(proxyBeanMethods = false)
@Import(JobTestConfiguration.class)
public class JobsDependencyIntegrationTest extends JobsIntegrationBootstrap {

    @Test
    void givenJobWithChildJobsExistsInDb_whenWeQueryByJobId_thenReturnsJobWithItsChildJobs() {
        jobExecutor.addJobProcessor(
                CustomJobProcessorFactory.getBuilder()
                        .name("my-job")
                        .build()
        );
        jobExecutor.addJobProcessor(
                CustomJobProcessorFactory.getBuilder()
                        .name("my-job_1")
                        .build()
        );
        jobExecutor.addJobProcessor(
                CustomJobProcessorFactory.getBuilder()
                        .name("my-job_2")
                        .build()
        );
        jobExecutor.addJobProcessor(
                CustomJobProcessorFactory.getBuilder()
                        .name("my-job_3")
                        .build()
        );
        jobExecutor.addJobProcessor(
                CustomJobProcessorFactory.getBuilder()
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

        res.body().prettyPrint();

        res.then()
                .statusCode(200)
                .body(
                        "result", equalTo("CREATED"),
                        "data.name", equalTo(CustomJobProcessorFactory.THROWABLE.name()),
                        "data.id", not(notANumber()),
                        "data.arguments", equalTo(null),
                        "data.uniqueJobId", equalTo(""),
                        "data.state", equalTo("READY")
                );

        // TODO: Verify execution via steps from leaf jobs up until the main job
    }

    // TODO: creation of job with dependencies when some are references

    // TODO: How to handle when some child references have failed?

    // TODO: How to handle when main job is a reference, but children are not?

    // TODO: How to handle when all jobs are references? -> Just return everything and don't create any new jobs (rows)
}
