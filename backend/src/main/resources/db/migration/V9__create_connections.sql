-- A connection points from a port of one node to another node of the same scenario (ADR 0011).
-- Both foreign keys include the scenario, so a connection cannot reach a node of another scenario,
-- and deleting a node deletes its connections.
CREATE TABLE connections (
    scenario_id BIGINT NOT NULL,
    source_key UUID NOT NULL,
    port VARCHAR(50) NOT NULL,
    target_key UUID NOT NULL,

    CONSTRAINT pk_connections
        PRIMARY KEY (scenario_id, source_key, port, target_key),

    CONSTRAINT fk_connections_source
        FOREIGN KEY (scenario_id, source_key) REFERENCES components (scenario_id, component_key) ON DELETE CASCADE,

    CONSTRAINT fk_connections_target
        FOREIGN KEY (scenario_id, target_key) REFERENCES components (scenario_id, component_key) ON DELETE CASCADE,

    CONSTRAINT ck_connections_port_not_blank
        CHECK (BTRIM(port) <> '')
);

CREATE INDEX ix_connections_target ON connections (scenario_id, target_key);
