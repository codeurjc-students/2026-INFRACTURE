import type { ExecutedComponentDTO } from "./ExecutedComponentDTO";
import type { ExecutedWorkloadDTO } from "./ExecutedWorkloadDTO";

// One run of an exact scenario revision. The phase says what the engine is doing, the stop cause
// why it stops, and the outcome how it ended once released.
export interface ExecutionDTO {
  id: number;
  scenarioId: number;
  scenarioRevision: number;
  phase: "RESERVED" | "STARTING" | "RUNNING" | "STOPPING" | "RELEASED";
  stopCause:
    "STOP_REQUESTED" | "TIME_LIMIT" | "FAILURE" | "INTERRUPTION" | null;
  outcome: "COMPLETED" | "FAILED" | "INTERRUPTED" | null;
  failureReason: string | null;
  // Failed cleanups in a row since the last Stop and the last error. They never change the outcome.
  cleanupFailures: number;
  cleanupError: string | null;
  // The automatic attempts are spent: the cleanup waits until the owner presses Stop again.
  cleanupRetryNeeded: boolean;
  createdAt: string;
  // Fixed when every node is healthy; the server ends the experiment at this time.
  deadlineAt: string | null;
  // Whole seconds until the deadline by the server's clock, or null when no countdown applies.
  secondsLeft: number | null;
  releasedAt: string | null;
  components: ExecutedComponentDTO[];
  // null when the scenario had no load.
  workload: ExecutedWorkloadDTO | null;
}
