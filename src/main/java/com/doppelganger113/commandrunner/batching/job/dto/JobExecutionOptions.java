package com.doppelganger113.commandrunner.batching.job.dto;

import java.util.HashMap;

public record JobExecutionOptions(
        String name,
        HashMap<String, Object> arguments
) {
}
