import { useEffect, useRef, useState } from "react";
import { HttpError } from "~/shared/api/api-fetch";
import {
  getCurrentExecution,
  runScenario,
  stopExecution,
} from "./api/execution-service";
import type { ExecutionDTO } from "./model/ExecutionDTO";

const GENERIC_ERROR = "Something went wrong. Try again in a moment.";
export const POLL_INTERVAL_MS = 2000;

// The scenario's latest execution and the Run and Stop requests.
export function useExecution(
  projectId: string,
  scenarioId: string,
  initial: ExecutionDTO | null,
) {
  const [execution, setExecution] = useState(initial);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef(false);

  // Returns the error the request failed with, or null when it succeeded or another one was in flight.
  async function send(request: () => Promise<ExecutionDTO>) {
    if (inFlight.current) return null;
    inFlight.current = true;
    setPending(true);
    setError(null);
    try {
      setExecution(await request());
      return null;
    } catch (caught) {
      setError(caught instanceof HttpError ? caught.message : GENERIC_ERROR);
      return caught;
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  // Reserved, starting, running or cleaning up: the experiment holds the scenario.
  const active = execution !== null && execution.phase !== "RELEASED";
  // A failed poll changes nothing, so this counter schedules the next one anyway.
  const [retries, setRetries] = useState(0);

  // While the experiment is active, asks for its state again every 2 s
  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const latest = await getCurrentExecution(projectId, scenarioId);
        if (!cancelled && latest) setExecution(latest);
      } catch {
        if (!cancelled) setRetries((count) => count + 1);
      }
    }, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [active, execution, retries, projectId, scenarioId]);

  // Asks for the latest execution, which another tab or session may have started.
  async function refresh() {
    const latest = await getCurrentExecution(projectId, scenarioId);
    if (latest) setExecution(latest);
    return latest ?? null;
  }

  // Polling runs only while an experiment is active, so coming back to the tab is when this one
  // learns that another tab or session reserved the scenario in the meantime.
  const refreshOnReturn = useRef(refresh);
  refreshOnReturn.current = refresh;
  useEffect(() => {
    if (active) return;
    function onReturn() {
      if (document.visibilityState === "visible") {
        refreshOnReturn.current().catch(() => {
          // Nothing changes on screen; the next return or save asks again.
        });
      }
    }
    window.addEventListener("focus", onReturn);
    document.addEventListener("visibilitychange", onReturn);
    return () => {
      window.removeEventListener("focus", onReturn);
      document.removeEventListener("visibilitychange", onReturn);
    };
  }, [active]);

  return {
    execution,
    active,
    // The backend is cleaning up on its own, so Stop has nothing to add.
    stopping:
      active && execution.stopCause !== null && !execution.cleanupRetryNeeded,
    // The cleanup gave up trying on its own: Stop asks for new attempts.
    retryCleanup: active && execution.cleanupRetryNeeded,
    pending,
    error,
    run: (revision: number) =>
      send(() => runScenario(projectId, scenarioId, revision)),
    stop: () => {
      if (execution) {
        void send(() => stopExecution(projectId, scenarioId, execution.id));
      }
    },
    refresh,
    // Replaces the backend's message when the editor already acted on it.
    showError: setError,
    dismissError: () => setError(null),
  };
}
