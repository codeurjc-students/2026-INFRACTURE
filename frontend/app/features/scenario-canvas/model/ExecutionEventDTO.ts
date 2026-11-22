// One thing that happened during an execution, as the event stream sends it (#92). The id grows with each
// event and is the cursor a reconnecting page resumes from. componentKey is null for the whole execution
// or, on a log line, for the load. The other fields depend on the kind and, for EVENT, on the type.
export interface ExecutionEventDTO {
  id: number;
  executionId: number;
  componentKey: string | null;
  kind: "STATE" | "EVENT" | "LOG";
  at: string;
  // STATE: what Docker says about the node.
  status?: "STARTING" | "HEALTHY" | "UNHEALTHY" | "EXITED";
  // EVENT: which lifecycle step it records.
  type?: "PHASE" | "STOP" | "CLEANUP_FAILED" | "RESPONSE" | "LOGS_OMITTED";
  phase?: "RESERVED" | "STARTING" | "RUNNING" | "STOPPING" | "RELEASED";
  cause?: "STOP_REQUESTED" | "TIME_LIMIT" | "FAILURE" | "INTERRUPTION";
  reason?: string;
  error?: string;
  attempt?: number;
  response?: string;
  count?: number;
  // LOG: the line, already cleaned of secrets and cut to 500 characters.
  line?: string;
}
