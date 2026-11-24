import { useEffect, useEffectEvent, useState } from "react";
import { getExecutionMetrics } from "./api/execution-service";
import type {
  ExecutionMetricsDTO,
  MetricSampleDTO,
} from "./model/ExecutionMetricsDTO";

const FIRST_INTERVAL_MS = 5_000;

// The CPU and memory series of every node of an execution (#93). It reads every stored page, then asks
// for what is new at the pace the server samples while the execution is live and the tab is visible. The
// read that starts after the execution ends is the last one, so the final samples are not missed.
export function useExecutionMetrics(
  projectId: string,
  scenarioId: string,
  executionId: number,
  live: boolean,
) {
  const [metrics, setMetrics] = useState<ExecutionMetricsDTO | null>(null);
  const [samples, setSamples] = useState<MetricSampleDTO[]>([]);
  const [failed, setFailed] = useState(false);
  // Always the latest value, without starting the series again when it changes.
  const isLive = useEffectEvent(() => live);

  useEffect(() => {
    let cancelled = false;
    let reading = false;
    let cursor = 0;
    let intervalMs = FIRST_INTERVAL_MS;
    let timer: ReturnType<typeof setTimeout> | undefined;
    // A read fell due while the tab was hidden.
    let due = false;
    setMetrics(null);
    setSamples([]);
    setFailed(false);

    async function read() {
      if (reading || cancelled) return;
      reading = true;
      clearTimeout(timer);
      const another = isLive();
      try {
        let page: ExecutionMetricsDTO;
        do {
          page = await getExecutionMetrics(
            projectId,
            scenarioId,
            executionId,
            cursor,
          );
          if (cancelled) return;
          const received = page.samples;
          cursor = page.nextAfter;
          intervalMs = page.intervalSeconds * 1000;
          setMetrics(page);
          setSamples((list) => [...list, ...received]);
        } while (page.hasMore);
        setFailed(false);
      } catch {
        if (cancelled) return;
        setFailed(true);
      }
      reading = false;
      if (another) schedule();
    }

    function schedule() {
      if (document.visibilityState === "hidden") {
        due = true;
      } else {
        timer = setTimeout(() => void read(), intervalMs);
      }
    }

    function onVisibilityChange() {
      if (document.visibilityState !== "hidden" && due) {
        due = false;
        void read();
      }
    }

    document.addEventListener("visibilitychange", onVisibilityChange);
    void read();
    return () => {
      cancelled = true;
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [projectId, scenarioId, executionId]);

  return { metrics, samples, failed };
}
