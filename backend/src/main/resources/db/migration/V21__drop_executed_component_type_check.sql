-- The history keeps the type each node ran with. The application decides which types exist, so adding one
-- (the Load Balancer, #144) must not need a migration of the history tables.
ALTER TABLE executed_components
    DROP CONSTRAINT ck_executed_components_type;
