CREATE TABLE IF NOT EXISTS jobs
(
    id             SERIAL       NOT NULL PRIMARY KEY,
    name           VARCHAR(250) NOT NULL,
    arguments      JSONB,
    arguments_hash VARCHAR(64),
    state          VARCHAR(30)  NOT NULL default 'READY',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    duration_ms    INTEGER,
    retry_count    INTEGER,
    retry_limit    INTEGER,
    parent_job_id  INTEGER,
    error          TEXT,
    FOREIGN KEY (parent_job_id) REFERENCES jobs (id)
);

CREATE UNIQUE INDEX name_arg_hash_idx ON jobs (name, arguments_hash);

COMMENT ON COLUMN jobs.state IS 'State in which the job is in, also a test for comment';
COMMENT ON COLUMN jobs.error IS 'Details of the error when the job fails';
COMMENT ON COLUMN jobs.parent_job_id IS 'Job can start multiple child jobs';