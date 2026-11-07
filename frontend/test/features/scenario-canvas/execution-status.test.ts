import { describe, expect, it } from "vitest";

import {
  executionView,
  nodeView,
} from "~/features/scenario-canvas/execution-status";
import type { ExecutionDTO } from "~/features/scenario-canvas/model/ExecutionDTO";

function execution(overrides: Partial<ExecutionDTO>): ExecutionDTO {
  return {
    id: 12,
    scenarioId: 3,
    scenarioRevision: 2,
    phase: "RESERVED",
    stopCause: null,
    outcome: null,
    failureReason: null,
    createdAt: "2026-11-07T10:00:00Z",
    releasedAt: null,
    components: [],
    ...overrides,
  };
}

describe("execution status", () => {
  it.each([
    [{ phase: "RESERVED" }, "Reserved", "recovering"],
    [{ phase: "STARTING" }, "Starting", "recovering"],
    [{ phase: "RUNNING" }, "Running", "healthy"],
    [{ phase: "RUNNING", stopCause: "STOP_REQUESTED" }, "Stopping", "paused"],
    [
      { phase: "STARTING", stopCause: "FAILURE" },
      "Failed, cleaning up",
      "failed",
    ],
    [
      { phase: "STOPPING", stopCause: "INTERRUPTION" },
      "Interrupted, cleaning up",
      "degraded",
    ],
    [{ phase: "RELEASED", outcome: "COMPLETED" }, "Completed", "paused"],
    [{ phase: "RELEASED", outcome: "FAILED" }, "Failed", "failed"],
    [{ phase: "RELEASED", outcome: "INTERRUPTED" }, "Interrupted", "degraded"],
  ] as const)("shows %o as %s", (overrides, label, tone) => {
    expect(executionView(execution(overrides))).toEqual({ label, tone });
  });

  it.each([
    ["HEALTHY", "Healthy", "healthy"],
    ["STARTING", "Starting", "recovering"],
    ["UNHEALTHY", "Unhealthy", "failed"],
    ["EXITED", "Stopped", "failed"],
  ] as const)("shows a %s node as %s", (status, label, tone) => {
    expect(nodeView(status)).toEqual({ label, tone });
  });
});
