-- Cumulative counter readings published by each node alongside its CPU and memory sample.
-- An absent row means that counter was not read at that moment, which is not zero.
CREATE TABLE execution_counter_samples (
    metric_sample_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    value DOUBLE PRECISION NOT NULL,

    CONSTRAINT pk_execution_counter_samples
        PRIMARY KEY (metric_sample_id, name),

    CONSTRAINT fk_execution_counter_samples_sample
        FOREIGN KEY (metric_sample_id) REFERENCES execution_metric_samples (id) ON DELETE CASCADE,

    CONSTRAINT ck_execution_counter_samples_value
        CHECK (value >= 0)
);
