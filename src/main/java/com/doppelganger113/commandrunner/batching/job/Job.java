package com.doppelganger113.commandrunner.batching.job;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.*;


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

    @Column()
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

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "parent_job_id", referencedColumnName = "id")
    private List<Job> children;

    public Job() {
    }

    public Job(String name) {
        this.name = name;
    }

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

    public List<Job> getChildren() {
        if (children == null) {
            return Collections.emptyList();
        }
        return children;
    }

    public void setChildren(List<Job> children) {
        this.children = children;
    }

    public void addChild(Job child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    public boolean isSameArgumentsJob(Job other) {
        if (this == other) return true;
        if (other == null) return false;

        return Objects.equals(name, other.name) && Objects.equals(arguments, other.arguments);
    }

    /**
     * Flattens the tree structure into a list.
     */
    public List<Job> flatten() {
        List<Job> flattened = new ArrayList<>();
        flattened.add(this);

        if (children == null) return flattened;

        for (Job job : children) {
            flattened.addAll(job.flatten());
        }
        return flattened;
    }

    public boolean replaceChildWith(Job job) {
        if (children == null || children.isEmpty()) return false;

        children = new ArrayList<>(children);
        for (ListIterator<Job> iterator = children.listIterator(); iterator.hasNext(); ) {
            Job child = iterator.next();
            if (child.isSameArgumentsJob(job)) {
                iterator.set(job);
                return true;
            }
            boolean wasReplaced = child.replaceChildWith(job);
            if (wasReplaced) {
                return true;
            }
        }

        return false;
    }

    public Optional<Job> findChildJobByNameAndArgs(String name, HashMap<String, Object> args) {
        if (children == null || children.isEmpty()) return Optional.empty();

        for (Job child : children) {
            Optional<Job> childJob = child.findChildJobByNameAndArgs(name, args);
            if (childJob.isPresent()) {
                return childJob;
            }
            if (child.name.equals(name) && child.arguments.equals(args)) {
                return Optional.of(child);
            }
        }

        return Optional.empty();
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
