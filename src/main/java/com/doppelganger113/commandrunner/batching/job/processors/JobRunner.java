package com.doppelganger113.commandrunner.batching.job.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;

public class JobRunner implements JobProcessor {

    private final Logger log = LoggerFactory.getLogger(JobRunner.class);

    @Override
    public String getName() {
        return "my_job";
    }

    @Override
    public void execute(HashMap<String, Object> arguments) {
        log.info("Executing job runner {}", arguments);
        try {
            Thread.sleep(Duration.ofSeconds(5));
            log.info("Executed job runner");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
