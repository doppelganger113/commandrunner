package com.doppelganger113.commandrunner.batching.job;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Objects;


@Entity(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonHashMapConverter.class)
    private HashMap<String, Object> arguments;

    @Column(name = "arguments_hash")
    private String argumentsHash;

    @Column(insertable = false)
    @Enumerated(EnumType.STRING)
    private JobState state;

    @Column(name = "created_at", insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "retry_limit")
    private Integer retryLimit;

    @Column(name = "parent_job_id")
    private Long parentJobId;

    private String error;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HashMap<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(HashMap<String, Object> arguments) {
        this.arguments = arguments;
    }

    public String getArgumentsHash() {
        return argumentsHash;
    }

    public void setArgumentsHash(String argumentsHash) {
        this.argumentsHash = argumentsHash;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime executedAt) {
        this.startedAt = executedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getRetryLimit() {
        return retryLimit;
    }

    public void setRetryLimit(Integer retryLimit) {
        this.retryLimit = retryLimit;
    }

    public Long getParentJobId() {
        return parentJobId;
    }

    public void setParentJobId(Long parentJobId) {
        this.parentJobId = parentJobId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public boolean hasEqualConfiguration(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;

        return Objects.equals(name, job.name)
                && Objects.equals(arguments, job.arguments)
                && Objects.equals(argumentsHash, job.argumentsHash)
                && Objects.equals(retryCount, job.retryCount)
                && Objects.equals(retryLimit, job.retryLimit)
                && Objects.equals(parentJobId, job.parentJobId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return Objects.equals(name, job.name)
                && Objects.equals(arguments, job.arguments)
                && Objects.equals(argumentsHash, job.argumentsHash)
                && state == job.state
                && Objects.equals(createdAt, job.createdAt)
                && Objects.equals(updatedAt, job.updatedAt)
                && Objects.equals(startedAt, job.startedAt)
                && Objects.equals(completedAt, job.completedAt)
                && Objects.equals(durationMs, job.durationMs)
                && Objects.equals(retryCount, job.retryCount)
                && Objects.equals(retryLimit, job.retryLimit)
                && Objects.equals(parentJobId, job.parentJobId)
                && Objects.equals(error, job.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arguments, argumentsHash, state, createdAt, updatedAt, startedAt, completedAt, durationMs, retryCount, retryLimit, parentJobId, error);
    }

    @Override
    public String toString() {
        return "Job{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", arguments=" + arguments +
                ", argumentsHash=" + argumentsHash +
                ", state=" + state +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", durationMs=" + durationMs +
                ", retryCount=" + retryCount +
                ", retryLimit=" + retryLimit +
                ", parentJobId=" + parentJobId +
                ", error=" + error +
                '}';
    }
}
