package com.doppelganger113.commandrunner.batching.job;

import com.doppelganger113.commandrunner.batching.job.dto.JobExecutionOptions;
import com.doppelganger113.commandrunner.hash.ShaHash;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobFactory {

    private final ShaHash shaHash;

    public JobFactory(ShaHash shaHash) {
        this.shaHash = shaHash;
    }

    private Job createInitialJobFrom(JobExecutionOptions options) {
        Job newJob = new Job();
        newJob.setName(options.name());
        newJob.setArguments(options.arguments());
        newJob.setArgumentsHash(shaHash.hash(options.arguments()));
        newJob.setState(JobState.READY);

        return newJob;
    }

    public Job from(JobExecutionOptions jobExecutionOptions) {
        Job job = createInitialJobFrom(jobExecutionOptions);

        if (jobExecutionOptions.jobs() == null || jobExecutionOptions.jobs().isEmpty()) {
            return job;
        }

        List<Job> children = new ArrayList<>();
        for (JobExecutionOptions child : jobExecutionOptions.jobs()) {
            children.add(from(child));
        }
        if (!children.isEmpty()) {
            job.setChildren(children);
        }

        return job;
    }
}
