package com.doppelganger113.commandrunner.batching.job;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends ListCrudRepository<Job, Long> {

    Optional<Job> findByNameAndStateNotInOrderByCreatedAtDesc(String name, List<JobState> states);

    Optional<Job> findFirstByNameAndArgumentsHashOrderByIdDesc(String name, String argumentsHash);

    @Transactional
    @Modifying
    @Query(
            value = "UPDATE jobs SET state = 'STOPPING', updated_at = NOW() WHERE id = ?1 AND (state = 'RUNNING' OR state = 'READY')",
            nativeQuery = true
    )
    void setJobToStop(Long id);

    @Transactional
    @Modifying
    @Query(
            value = "UPDATE jobs SET state = 'STOPPED', duration_ms = EXTRACT(MILLISECONDS FROM (NOW() - started_at))  WHERE id = ?1 AND state = 'STOPPING'",
            nativeQuery = true
    )
    void setJobStopped(Long id);

    @Modifying
    @Query(value = "UPDATE jobs SET state = 'RUNNING', started_at = NOW() WHERE id = ?1", nativeQuery = true)
    void setJobStarted(Long id);

    @Modifying
    @Query(
            value = "UPDATE jobs " +
                    "SET state = 'COMPLETED', completed_at = NOW(), duration_ms = EXTRACT(MILLISECONDS FROM (NOW() - started_at)) " +
                    "WHERE id = ?1",
            nativeQuery = true
    )
    void setJobCompleted(Long id);

    @Modifying
    @Query(
            value = "UPDATE jobs SET state = 'FAILED', completed_at = NOW(), duration_ms = EXTRACT(MILLISECONDS FROM (NOW() - started_at)), error = ?2 WHERE id = ?1",
            nativeQuery = true
    )
    void setJobFailed(Long id, String error);

    // The query needs all fields
    @Query(
            value = "WITH RECURSIVE job_dependencies AS (SELECT id, name, arguments, arguments_hash, state, created_at, updated_at, started_at, completed_at, duration_ms, retry_count, retry_limit, parent_job_id, error" +
                    "        FROM jobs" +
                    "                                            WHERE id = ?" +
                    "                                            UNION" +
                    "                                            SELECT j.id, j.name, j.arguments, j.arguments_hash, j.state, j.created_at, j.updated_at, j.started_at, j.completed_at, j.duration_ms, j.retry_count, j.retry_limit, j.parent_job_id, j.error" +
                    "                                            FROM jobs j" +
                    "                                            INNER JOIN job_dependencies s ON s.id = j.parent_job_id)" +
                    "SELECT *" +
                    "FROM job_dependencies;",
            nativeQuery = true
    )
    List<Job> findJobByIdAndItsDependencies(Long id);

    List<Job> findJobsByParentJobId(Long parentJobId);
}
