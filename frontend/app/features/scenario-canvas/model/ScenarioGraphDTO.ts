import type { ComponentDTO } from "./ComponentDTO";
import type { ConnectionDTO } from "./ConnectionDTO";
import type { ValidationIssueDTO } from "./ValidationIssueDTO";
import type { WorkloadDTO } from "./WorkloadDTO";

// Response of GET and PUT /graph: the revision, the components, their connections, the load and
// contract issues.
export interface ScenarioGraphDTO {
  revision: number;
  components: ComponentDTO[];
  connections: ConnectionDTO[];
  // null when the scenario has no load.
  workload: WorkloadDTO | null;
  issues: ValidationIssueDTO[];
}
