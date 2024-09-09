package com.doppelganger113.commandrunner.batching.job.processors;

import java.util.HashMap;

public interface JobProcessor {
    String getName();

    void execute(HashMap<String, Object> arguments);
}
