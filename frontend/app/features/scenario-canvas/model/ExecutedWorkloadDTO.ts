// The load as the execution froze it and, once it ended, what k6 reported. summaryCollectedAt and
// completed are null until then; each summary value is null when k6 did not report it.
export interface ExecutedWorkloadDTO {
  targetKey: string;
  profile: string;
  parameters: Record<string, number>;
  seed: number;
  summaryCollectedAt: string | null;
  // false when Stop or a failure cut the load short.
  completed: boolean | null;
  summary: {
    requests: number | null;
    failedRequests: number | null;
    droppedIterations: number | null;
    averageLatencyMs: number | null;
    p95LatencyMs: number | null;
  } | null;
}
