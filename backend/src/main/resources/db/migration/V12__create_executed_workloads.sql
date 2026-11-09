-- The load an execution ran: a frozen copy of the scenario Workload (ADR 0012) and, once the load ends,
-- the aggregate summary k6 reported. There is never one row per request.
CREATE TABLE executed_workloads (
    execution_id BIGINT NOT NULL,
    target_key UUID NOT NULL,
    profile VARCHAR(50) NOT NULL,
    parameters JSONB NOT NULL,
    seed INTEGER NOT NULL,
    -- Null until the summary is collected; each value stays null when k6 did not report it.
    summary_collected_at TIMESTAMP WITH TIME ZONE,
    completed BOOLEAN,
    requests BIGINT,
    failed_requests BIGINT,
    dropped_iterations BIGINT,
    average_latency_ms DOUBLE PRECISION,
    p95_latency_ms DOUBLE PRECISION,

    CONSTRAINT pk_executed_workloads
        PRIMARY KEY (execution_id),

    CONSTRAINT fk_executed_workloads_execution
        FOREIGN KEY (execution_id) REFERENCES executions (id) ON DELETE CASCADE,

    CONSTRAINT fk_executed_workloads_target
        FOREIGN KEY (execution_id, target_key)
            REFERENCES executed_components (execution_id, component_key) ON DELETE CASCADE,

    CONSTRAINT ck_executed_workloads_parameters_object
        CHECK (jsonb_typeof(parameters) = 'object'),

    CONSTRAINT ck_executed_workloads_summary
        CHECK ((summary_collected_at IS NULL) = (completed IS NULL))
);
