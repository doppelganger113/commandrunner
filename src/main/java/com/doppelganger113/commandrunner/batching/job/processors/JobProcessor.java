package com.doppelganger113.commandrunner.batching.job.processors;

import java.util.HashMap;

public interface JobProcessor {
    String getName();

    default void before(HashMap<String, Object> arguments) {
    }

    void execute(HashMap<String, Object> arguments);

    default void after(HashMap<String, Object> arguments) {
    }
}
