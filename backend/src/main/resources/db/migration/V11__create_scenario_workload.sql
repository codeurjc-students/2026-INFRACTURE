-- The load applied to a scenario (ADR 0012): at most one per scenario, saved with the graph and its revision.
CREATE TABLE scenario_workload (
    scenario_id BIGINT NOT NULL,
    target_key UUID,
    profile VARCHAR(50),
    parameters JSONB NOT NULL,
    seed INTEGER NOT NULL,

    CONSTRAINT pk_scenario_workload
        PRIMARY KEY (scenario_id),

    CONSTRAINT fk_scenario_workload_scenario
        FOREIGN KEY (scenario_id) REFERENCES scenarios (id) ON DELETE CASCADE,

    -- Deleting the target only empties the reference, so the load settings are not lost.
    CONSTRAINT fk_scenario_workload_target
        FOREIGN KEY (scenario_id, target_key)
            REFERENCES components (scenario_id, component_key) ON DELETE SET NULL (target_key),

    CONSTRAINT ck_scenario_workload_parameters_object
        CHECK (jsonb_typeof(parameters) = 'object')
);
