package com.doppelganger113.commandrunner.batching.job.dto;

public enum JobExecutionDescription {
    CREATED("CREATED"),
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    ;
    private final String value;

    JobExecutionDescription(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
