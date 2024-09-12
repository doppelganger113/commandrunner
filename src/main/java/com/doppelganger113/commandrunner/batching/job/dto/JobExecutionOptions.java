package com.doppelganger113.commandrunner.batching.job.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public record JobExecutionOptions(
        @NotEmpty
        @NotNull
        String name,
        HashMap<String, Object> arguments,
        List<JobExecutionOptions> jobs
) {
    public boolean hasDuplicate() {
        if (jobs == null || jobs.isEmpty()) {
            return false;
        }

        List<JobExecutionOptions> flattenedJobs = flatten();

        for (int i = 0; i < flattenedJobs.size(); i++) {
            JobExecutionOptions jobExecutionOptions = flattenedJobs.get(i);
            for (int j = i; j < flattenedJobs.size(); j++) {
                JobExecutionOptions jobToCompare = flattenedJobs.get(j);
                if (jobExecutionOptions != jobToCompare && jobExecutionOptions.equals(jobToCompare)) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<JobExecutionOptions> flatten() {
        List<JobExecutionOptions> flattened = new ArrayList<>();
        flattened.add(new JobExecutionOptions(name, arguments, null));

        if (jobs == null) return flattened;

        for (JobExecutionOptions job : jobs) {
            flattened.addAll(job.flatten());
        }
        return flattened;
    }
}
