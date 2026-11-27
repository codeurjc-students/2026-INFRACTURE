-- Each node of the stored plan groups how its container starts (image, command, user, in-memory storage) and
-- what it is wired with (variables, password references, dependencies), and its health probe names its type.
-- Plans stored so far keep every value they ran with; the fields they did not have yet (user, storage, secrets,
-- dependencies) read as none.
UPDATE executions
SET plan = jsonb_set(plan, '{nodes}', COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
        'key', node -> 'key',
        'host', node -> 'host',
        'container', jsonb_build_object(
            'image', node -> 'image',
            'command', COALESCE(node -> 'command', 'null'::JSONB),
            'user', COALESCE(node -> 'user', 'null'::JSONB),
            'storage', COALESCE(NULLIF(node -> 'storage', 'null'::JSONB), '[]'::JSONB)),
        'wiring', jsonb_build_object(
            'environment', COALESCE(NULLIF(node -> 'environment', 'null'::JSONB), '{}'::JSONB),
            'secrets', COALESCE(NULLIF(node -> 'secrets', 'null'::JSONB), '{}'::JSONB),
            'dependencies', COALESCE(NULLIF(node -> 'dependencies', 'null'::JSONB), '[]'::JSONB)),
        'health', (node -> 'health') || jsonb_build_object('type',
            CASE WHEN (node -> 'health') ? 'command' THEN 'command' ELSE 'http' END),
        'evidence', node -> 'evidence',
        'limits', node -> 'limits') ORDER BY position)
    FROM jsonb_array_elements(plan -> 'nodes') WITH ORDINALITY AS nodes(node, position)), '[]'::JSONB))
WHERE plan ? 'nodes'
  AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(plan -> 'nodes') AS nodes(node) WHERE node ? 'container');
