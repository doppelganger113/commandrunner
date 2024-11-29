package com.doppelganger113.commandrunner.batching.job;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@EnableScheduling
@Component
public class JobScheduler implements DisposableBean {

    private final JobExecutor jobExecutor;
    private final int jobCount;
    private final ExecutorService executorService;

    public JobScheduler(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
        this.jobCount = jobExecutor.getJobCount();
        this.executorService = Executors.newFixedThreadPool(jobCount);
    }

    @Async
    void triggerJob(String jobName) {
        System.currentTimeMillis();
        // TODO: triggers async and the transaction will make sure the same job isn't executed twice
//        loadJobsAndExecute();
    }

//    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
//    void loadJobsAndExecute() throws RuntimeException {
//        System.out.println("loading jobs and executing...");
//
//        List<Callable<Integer>> tasks = new ArrayList<>();
//
//        for (int i = 0; i < jobCount; i++) {
//            final int taskId = i;
//            tasks.add(() -> {
//                int time = (taskId + 1) * 1_000;
//                System.out.println("Executing job " + taskId + " for " + time);
//                Thread.sleep(time);
//                return taskId;
//            });
//        }
//
//        List<Future<Integer>> futures = null;
//        try {
//            futures = executorService.invokeAll(tasks);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        for (Future<Integer> future : futures) {
//            try {
//                int taskId = future.get();
//                System.out.println("Completed task: " + taskId);
//            } catch (ExecutionException | InterruptedException e) {
//                System.out.println(e.getMessage() + " " + e.getCause());
//            }
//        }
//        System.out.println("Done executing jobs");
//
//        // TODO: here we read from the database all the jobs we need to execute
//
//        // TODO: check lock skipping, so that we can separate transactions by job name
//    }

    @Override
    public void destroy() {
        executorService.shutdown();
    }
}
