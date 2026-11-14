-- A backend restart that finds an execution open records when it found it and in which phase (#90).
-- Only the first restart is kept, and an interruption is the worst stop cause, so the cause stays INTERRUPTION.
ALTER TABLE executions
    ADD COLUMN interrupted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN interrupted_phase VARCHAR(20),
    ADD CONSTRAINT ck_executions_interrupted_phase
        CHECK (interrupted_phase IN ('RESERVED', 'STARTING', 'RUNNING', 'STOPPING')),
    ADD CONSTRAINT ck_executions_interruption
        CHECK ((interrupted_at IS NULL) = (interrupted_phase IS NULL)
            AND (interrupted_at IS NULL OR stop_cause = 'INTERRUPTION'));
