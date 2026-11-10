import type { ExecutedComponentDTO } from "./model/ExecutedComponentDTO";
import type { ExecutionDTO } from "./model/ExecutionDTO";

// The state vocabulary of DESIGN.md. Every state carries a word and an icon, never only a colour.
export type Tone = "healthy" | "recovering" | "failed" | "degraded" | "paused";

export interface StatusView {
  label: string;
  tone: Tone;
}

export type NodeStatus = NonNullable<ExecutedComponentDTO["status"]>;

// What the experiment as a whole is doing, in the words the top bar shows.
export function executionView(execution: ExecutionDTO): StatusView {
  if (execution.phase === "RELEASED") {
    if (execution.outcome === "FAILED")
      return { label: "Failed", tone: "failed" };
    if (execution.outcome === "INTERRUPTED") {
      return { label: "Interrupted", tone: "degraded" };
    }
    return { label: "Completed", tone: "paused" };
  }
  // A failed cleanup keeps the experiment, so it shows before the cause.
  if (execution.cleanupRetryNeeded) {
    return { label: "Cleanup failed", tone: "failed" };
  }
  if (execution.cleanupFailures > 0) {
    return { label: "Cleanup failed, retrying", tone: "degraded" };
  }
  // A cause means the backend is already cleaning up, whatever the phase says.
  if (execution.stopCause === "FAILURE") {
    return { label: "Failed, cleaning up", tone: "failed" };
  }
  if (execution.stopCause === "INTERRUPTION") {
    return { label: "Interrupted, cleaning up", tone: "degraded" };
  }
  if (execution.stopCause === "STOP_REQUESTED") {
    return { label: "Stopping", tone: "paused" };
  }
  if (execution.phase === "RUNNING")
    return { label: "Running", tone: "healthy" };
  return {
    label: execution.phase === "RESERVED" ? "Reserved" : "Starting",
    tone: "recovering",
  };
}

// What Docker says about one node.
export function nodeView(status: NodeStatus): StatusView {
  switch (status) {
    case "HEALTHY":
      return { label: "Healthy", tone: "healthy" };
    case "STARTING":
      return { label: "Starting", tone: "recovering" };
    case "UNHEALTHY":
      return { label: "Unhealthy", tone: "failed" };
    case "EXITED":
      return { label: "Stopped", tone: "failed" };
  }
}

// k6 is sending requests right now: the experiment runs, nothing stops it and the load has not ended.
export function isSendingTraffic(execution: ExecutionDTO | null) {
  return (
    execution?.workload != null &&
    execution.workload.summaryCollectedAt === null &&
    execution.phase === "RUNNING" &&
    execution.stopCause === null
  );
}

// What the load of an execution is doing, or null when the execution had no load. Once k6 reported,
// its summary says whether it ran its whole duration.
export function loadView(execution: ExecutionDTO): StatusView | null {
  const { workload } = execution;
  if (!workload) return null;
  if (workload.summaryCollectedAt !== null) {
    return workload.completed
      ? { label: "Finished", tone: "paused" }
      : { label: "Stopped early", tone: "paused" };
  }
  if (execution.phase === "RELEASED") {
    return { label: "No summary", tone: "degraded" };
  }
  if (execution.stopCause !== null)
    return { label: "Stopping", tone: "paused" };
  if (isSendingTraffic(execution)) {
    return { label: "Sending traffic", tone: "healthy" };
  }
  return { label: "Waiting for healthy components", tone: "recovering" };
}

// Text colour of each tone, from the tokens in app.css.
export const toneText: Record<Tone, string> = {
  healthy: "text-healthy-text",
  recovering: "text-recovering-text",
  failed: "text-destructive",
  degraded: "text-degraded-text",
  paused: "text-muted-foreground",
};
