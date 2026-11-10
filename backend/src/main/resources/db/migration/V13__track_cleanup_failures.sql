-- A failed cleanup is tracked apart from the stop cause, so it never changes how the execution ended (#88).
-- The failures count the attempts in a row since the last Stop; the error is the last one Docker reported.
ALTER TABLE executions
    ADD COLUMN cleanup_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN cleanup_error VARCHAR(1000),
    ADD COLUMN cleanup_failed_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT ck_executions_cleanup_failures
        CHECK (cleanup_failures >= 0),
    ADD CONSTRAINT ck_executions_cleanup_error
        CHECK ((cleanup_error IS NULL) = (cleanup_failed_at IS NULL));
