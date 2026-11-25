-- The stored plan stops being a Compose file: the runtime engine renders Compose from it, and the plan keeps
-- only Infracture's own terms (nodes by key, health probe, limits and the load). Plans stored so far hold
-- HTTP services named c-<node key>, their wget health check and, optionally, the k6 service "workload".
-- They are rewritten to the shape PlanCompiler writes now, keeping every value they ran with.
CREATE FUNCTION pg_temp.typed_limits(service JSONB) RETURNS JSONB AS $$
    SELECT jsonb_build_object(
        'cpus', (service #>> '{deploy,resources,limits,cpus}')::NUMERIC,
        'memoryBytes', rtrim(service #>> '{deploy,resources,limits,memory}', 'M')::BIGINT * 1048576,
        'pids', (service #>> '{deploy,resources,limits,pids}')::INTEGER)
$$ LANGUAGE SQL IMMUTABLE;

-- "http://127.0.0.1:8080/health" -> port 8080 and path /health; "2s" -> 2.
CREATE FUNCTION pg_temp.typed_probe(healthcheck JSONB) RETURNS JSONB AS $$
    SELECT jsonb_build_object(
        'port', substring(healthcheck #>> '{test,3}' FROM '^http://[^:/]+:([0-9]+)')::INTEGER,
        'path', substring(healthcheck #>> '{test,3}' FROM '^http://[^/]+(/.*)$'),
        'intervalSeconds', rtrim(healthcheck ->> 'interval', 's')::INTEGER,
        'timeoutSeconds', rtrim(healthcheck ->> 'timeout', 's')::INTEGER,
        'retries', (healthcheck ->> 'retries')::INTEGER,
        'startPeriodSeconds', rtrim(healthcheck ->> 'start_period', 's')::INTEGER)
$$ LANGUAGE SQL IMMUTABLE;

UPDATE executions
SET plan = jsonb_build_object(
    'project', plan ->> 'name',
    'labels', plan #> '{networks,exec,labels}',
    'nodes', COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
            'key', substring(service.name FROM 3),
            'host', service.name,
            'image', service.value ->> 'image',
            'command', NULL,
            'environment', service.value -> 'environment',
            'health', pg_temp.typed_probe(service.value -> 'healthcheck'),
            -- HTTP services answered the backend on the port of their health check.
            'evidence', 'http://' || service.name || ':'
                || substring(service.value #>> '{healthcheck,test,3}' FROM '^http://[^:/]+:([0-9]+)') || '/',
            'limits', pg_temp.typed_limits(service.value)) ORDER BY service.name)
        FROM jsonb_each(plan -> 'services') AS service(name, value)
        WHERE service.name <> 'workload'), '[]'::JSONB),
    'load', (
        SELECT jsonb_build_object(
            'image', load.value ->> 'image',
            'command', NULL,
            'environment', load.value -> 'environment',
            'target', substring((SELECT jsonb_object_keys(load.value -> 'depends_on') LIMIT 1) FROM 3),
            'limits', pg_temp.typed_limits(load.value))
        FROM jsonb_each(plan -> 'services') AS load(name, value)
        WHERE load.name = 'workload'))
WHERE plan ? 'services';
