// The scenario load (ADR 0012): a setting of the scenario, not a node. targetKey is null when the
// target was deleted or not chosen yet; the backend reports it as an issue.
export interface WorkloadDTO {
  targetKey: string | null;
  profile: string | null;
  parameters: Record<string, number>;
  seed: number;
}
