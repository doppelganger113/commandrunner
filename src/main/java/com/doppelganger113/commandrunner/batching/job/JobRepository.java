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
}
