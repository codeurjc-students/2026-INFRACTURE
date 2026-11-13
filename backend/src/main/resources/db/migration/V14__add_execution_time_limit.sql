ALTER TABLE executions
    ADD COLUMN deadline_at TIMESTAMP WITH TIME ZONE;

-- An execution already running when this migration applies keeps the limit it had: 30 minutes from its creation.
UPDATE executions
SET deadline_at = created_at + INTERVAL '30 minutes'
WHERE phase = 'RUNNING';

ALTER TABLE executions
    DROP CONSTRAINT ck_executions_stop_cause,
    ADD CONSTRAINT ck_executions_stop_cause
        CHECK (stop_cause IN ('STOP_REQUESTED', 'TIME_LIMIT', 'FAILURE', 'INTERRUPTION')),
    ADD CONSTRAINT ck_executions_running_deadline
        CHECK (phase <> 'RUNNING' OR deadline_at IS NOT NULL);
