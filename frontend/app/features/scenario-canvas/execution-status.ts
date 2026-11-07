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

// Text colour of each tone, from the tokens in app.css.
export const toneText: Record<Tone, string> = {
  healthy: "text-healthy-text",
  recovering: "text-recovering-text",
  failed: "text-destructive",
  degraded: "text-degraded-text",
  paused: "text-muted-foreground",
};
