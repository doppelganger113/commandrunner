package com.doppelganger113.commandrunner.batching.job.processors;

import java.util.HashMap;

/**
 * Used as a parent job when multiple jobs without a real parent job need to be executed in order or without.
 */
public class EmptyRunner implements JobProcessor {
    @Override
    public String getName() {
        return "empty";
    }

    @Override
    public void execute(HashMap<String, Object> arguments) {
    }
}
