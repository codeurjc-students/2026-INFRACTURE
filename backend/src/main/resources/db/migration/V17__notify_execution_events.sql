-- Tells every backend that an execution has new events (#92), so its open streams read them at once
-- instead of asking the database every second. PostgreSQL sends the notice only when the transaction
-- commits, and sends one notice per execution even when the transaction inserted many rows.
CREATE FUNCTION notify_execution_event() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_notify('execution_events', NEW.execution_id::TEXT);
    RETURN NULL;
END;
$$;

CREATE TRIGGER tr_execution_events_notify
    AFTER INSERT ON execution_events
    FOR EACH ROW EXECUTE FUNCTION notify_execution_event();
