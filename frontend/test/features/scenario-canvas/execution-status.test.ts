import { describe, expect, it } from "vitest";

import {
  executionView,
  isSendingTraffic,
  loadView,
  nodeView,
} from "~/features/scenario-canvas/execution-status";
import type { ExecutedWorkloadDTO } from "~/features/scenario-canvas/model/ExecutedWorkloadDTO";
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
    workload: null,
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

  const frozen: ExecutedWorkloadDTO = {
    targetKey: "a",
    profile: "CONSTANT_HTTP",
    parameters: { requestsPerSecond: 5 },
    seed: 7,
    summaryCollectedAt: null,
    completed: null,
    summary: null,
  };
  const ended = { ...frozen, summaryCollectedAt: "2026-11-09T10:00:00Z" };

  it.each([
    [
      { phase: "STARTING", workload: frozen },
      "Waiting for healthy components",
      "recovering",
    ],
    [{ phase: "RUNNING", workload: frozen }, "Sending traffic", "healthy"],
    [
      { phase: "RUNNING", stopCause: "STOP_REQUESTED", workload: frozen },
      "Stopping",
      "paused",
    ],
    [
      { phase: "RUNNING", workload: { ...ended, completed: true } },
      "Finished",
      "paused",
    ],
    [
      { phase: "RELEASED", workload: { ...ended, completed: false } },
      "Stopped early",
      "paused",
    ],
    [{ phase: "RELEASED", workload: frozen }, "No summary", "degraded"],
  ] as const)("shows the load of %o as %s", (overrides, label, tone) => {
    expect(loadView(execution(overrides as Partial<ExecutionDTO>))).toEqual({
      label,
      tone,
    });
  });

  it("shows nothing for an execution without load", () => {
    expect(loadView(execution({ phase: "RUNNING" }))).toBeNull();
  });

  it("says k6 sends traffic only while the experiment runs and the load has not ended", () => {
    expect(
      isSendingTraffic(execution({ phase: "RUNNING", workload: frozen })),
    ).toBe(true);
    expect(
      isSendingTraffic(execution({ phase: "STARTING", workload: frozen })),
    ).toBe(false);
    expect(
      isSendingTraffic(
        execution({
          phase: "RUNNING",
          stopCause: "STOP_REQUESTED",
          workload: frozen,
        }),
      ),
    ).toBe(false);
    expect(
      isSendingTraffic(execution({ phase: "RUNNING", workload: ended })),
    ).toBe(false);
    expect(isSendingTraffic(null)).toBe(false);
  });
});
