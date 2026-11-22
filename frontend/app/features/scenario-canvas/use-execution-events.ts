import { useEffect, useEffectEvent, useState } from "react";
import { renewSession } from "~/shared/api/api-fetch";
import { executionEventsUrl } from "./api/execution-service";
import type { ExecutionEventDTO } from "./model/ExecutionEventDTO";

// The page keeps the latest events only; the backend keeps the rest.
export const MAX_EVENTS = 500;

// open: events arrive. reconnecting: the connection was cut and the browser is trying again.
// failed: the session could not be renewed or the server refused the stream. closed: the execution ended
// and every event arrived, or there is nothing to follow.
export type StreamState =
  | "connecting"
  | "open"
  | "reconnecting"
  | "failed"
  | "closed";

// Follows one execution's event stream (#92). The stream ends every 5 minutes with the access token: the
// server sends "renew", the page renews the session and reopens after the last event, so nothing is lost
// or repeated and the user sees no reconnection. onChange runs for every state or lifecycle event, so the
// caller can read the execution again; log lines only join the list.
export function useExecutionEvents(
  projectId: string,
  scenarioId: string,
  executionId: number | null,
  onChange: () => void,
) {
  const [events, setEvents] = useState<ExecutionEventDTO[]>([]);
  const [state, setState] = useState<StreamState>("closed");
  // Always the latest onChange, without reopening the stream when it changes.
  const notifyChange = useEffectEvent(onChange);

  useEffect(() => {
    if (executionId === null) return;
    const id = executionId;
    let source: EventSource | null = null;
    let cursor = 0;
    let finished = false;
    // Refusals in a row without the stream opening; a second one means renewing does not help.
    let refusals = 0;
    setEvents([]);
    setState("connecting");

    function open() {
      if (finished) return;
      const current = new EventSource(
        executionEventsUrl(projectId, scenarioId, id, cursor),
      );
      source = current;
      current.onopen = () => {
        refusals = 0;
        setState("open");
      };
      for (const kind of ["state", "event", "log"]) {
        current.addEventListener(kind, (message) =>
          receive(JSON.parse((message as MessageEvent<string>).data)),
        );
      }
      // The cursor is gone, for example purged: the page reads the execution again and takes what follows.
      current.addEventListener("reset", () => {
        cursor = 0;
        notifyChange();
      });
      current.addEventListener("renew", (message) => {
        cursor = JSON.parse((message as MessageEvent<string>).data).after;
        current.close();
        void reopen();
      });
      current.onerror = () => {
        if (current.readyState !== EventSource.CLOSED) {
          // The browser reconnects on its own and resumes with Last-Event-ID.
          setState("reconnecting");
          return;
        }
        current.close();
        refusals += 1;
        if (refusals > 1) {
          setState("failed");
          return;
        }
        setState("reconnecting");
        void reopen();
      };
    }

    function receive(event: ExecutionEventDTO) {
      if (event.id <= cursor) return;
      cursor = event.id;
      setEvents((list) => [...list, event].slice(-MAX_EVENTS));
      if (event.kind !== "LOG") notifyChange();
      if (event.type === "PHASE" && event.phase === "RELEASED") {
        finished = true;
        source?.close();
        setState("closed");
      }
    }

    async function reopen() {
      if (await renewSession()) {
        open();
      } else if (!finished) {
        setState("failed");
      }
    }

    open();
    return () => {
      finished = true;
      source?.close();
    };
  }, [projectId, scenarioId, executionId]);

  return { events, state };
}
