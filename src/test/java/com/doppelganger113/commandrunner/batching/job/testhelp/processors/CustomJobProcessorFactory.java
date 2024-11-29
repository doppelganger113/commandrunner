package com.doppelganger113.commandrunner.batching.job.testhelp.processors;

import java.util.concurrent.LinkedBlockingDeque;

public class CustomJobProcessorFactory {
    // This number is taken as fair enough time for CI/CD when a slow machine is executing tests
    public static final int JOB_SLOWDOWN_MLS = 500;

    public static CustomJobProcessor DEFAULT = getBuilder()
            .name("custom_processor")
            .build();
    public static CustomJobProcessor THROWABLE = getBuilder()
            .name("failing_processor")
            .throwable(new RuntimeException("Failed again"))
            .build();
    public static CustomJobProcessor SLOW = getBuilder()
            .durationMs(JOB_SLOWDOWN_MLS)
            .name("slow_processor")
            .build();

    public static CustomJobProcessor SLOW_THROWABLE = getBuilder()
            .name("slow_failing")
            .durationMs(JOB_SLOWDOWN_MLS)
            .throwable(new RuntimeException("failed later"))
            .build();

    public static class Builder {
        private Integer durationMs;
        private Throwable throwable;
        private String name;

        public Builder durationMs(Integer durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public CustomJobProcessor build() {
            return new CustomJobProcessor(name, durationMs, throwable, new LinkedBlockingDeque<>());
        }
    }

    public static Builder getBuilder() {
        return new Builder();
    }
}
