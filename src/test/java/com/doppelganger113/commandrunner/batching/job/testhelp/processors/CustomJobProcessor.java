package com.doppelganger113.commandrunner.batching.job.testhelp.processors;

import com.doppelganger113.commandrunner.batching.job.processors.JobProcessor;

import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Used for assistance during tests to customize execution duration, if it's throwable and/or to await its completion.
 * Check {@link CustomJobProcessorFactory} for existing types of processors or for extension of new build processes.
 */
public record CustomJobProcessor(
        String name,
        Integer durationMs,
        Throwable throwable,
        LinkedBlockingDeque<Boolean> blockingDeque
) implements JobProcessor {

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void execute(HashMap<String, Object> arguments) {
        if (durationMs != null && durationMs > 0) {
            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (throwable != null) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void after(HashMap<String, Object> arguments) {
        if (blockingDeque != null) {
            blockingDeque.add(true);
        }
    }

    public void waitForCompletionOrFail() {
        try {
            var result = blockingDeque.pollFirst(1, TimeUnit.SECONDS);
            if (result == null) {
                throw new RuntimeException("Timed out pooling from dequeue");
            }
            if (!blockingDeque.isEmpty()) {
                throw new RuntimeException(
                        "Size is bigger, did you expect multiple executions of the same job?"
                );
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void clearQueue() {
        blockingDeque.clear();
    }
}
