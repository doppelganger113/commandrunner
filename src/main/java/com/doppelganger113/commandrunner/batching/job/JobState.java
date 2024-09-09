package com.doppelganger113.commandrunner.batching.job;

public enum JobState {
    READY("READY"),
    RUNNING("RUNNING"),
    STOPPING("STOPPING"),
    STOPPED("STOPPED"),
    FAILED("FAILED"),
    COMPLETED("COMPLETED");

    private final String state;

    JobState(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }
}
