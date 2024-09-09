package com.doppelganger113.commandrunner;

import com.doppelganger113.commandrunner.batching.job.JobRepository;
import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConfiguration(proxyBeanMethods = false)
class JobsIntegrationTests {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:latest"
    ).withReuse(true);

    @LocalServerPort
    private Integer port;

    @Autowired
    private JobRepository jobRepository;


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        System.out.println("URL: " + postgres.getJdbcUrl());
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
    }

    @Test
    void exampleTest() {
        given()
                .body(new JobExecutionOptions("my_job", null))
                .contentType(ContentType.JSON)
                .when()
                .post("/jobs")
                .then()
                .statusCode(200)
                .body("id", equalTo(1));


        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/jobs")
                .then()
                .statusCode(200)
                .body(".", hasSize(1));
    }
}
