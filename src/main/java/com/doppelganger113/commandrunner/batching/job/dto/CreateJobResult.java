package com.doppelganger113.commandrunner.batching.job.dto;

public enum CreateJobResult {
    CREATED("CREATED"),
    REFERENCED("REFERENCED"),
    ;
    private final String value;

    CreateJobResult(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "CreateJobResult{" +
                "value='" + value + '\'' +
                '}';
    }
}
