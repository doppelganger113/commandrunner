CREATE TABLE IF NOT EXISTS jobs
(
    id               SERIAL       NOT NULL PRIMARY KEY,
    name             VARCHAR(250) NOT NULL,
    arguments        JSONB,
    unique_job_id    VARCHAR(250),
    state            VARCHAR(30) default 'READY',
    created_at       TIMESTAMPTZ DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    duration_ms      INTEGER,
    retry_count      INTEGER,
    retry_limit      INTEGER,
    parent_job_id    INTEGER,
    reference_job_id INTEGER,
    error            TEXT,
    CHECK (parent_job_id <> id),
    CHECK (jobs.reference_job_id <> id),
    CHECK (jobs.reference_job_id <> jobs.parent_job_id),
    FOREIGN KEY (parent_job_id) REFERENCES jobs (id),
    FOREIGN KEY (reference_job_id) REFERENCES jobs (id)
);

CREATE UNIQUE INDEX name_arg_hash_idx ON jobs (name, unique_job_id);

COMMENT ON COLUMN jobs.state IS 'State in which the job is in, also a test for comment';
COMMENT ON COLUMN jobs.error IS 'Details of the error when the job fails';
COMMENT ON COLUMN jobs.unique_job_id IS 'Calculated id, usually from arguments used for unique checks';
COMMENT ON COLUMN jobs.parent_job_id IS 'Job can start multiple child jobs, top parent is null';
COMMENT ON COLUMN jobs.reference_job_id IS 'Reference to the job with the same arguments_hash';