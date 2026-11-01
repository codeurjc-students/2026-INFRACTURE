import type { ComponentDTO } from "./ComponentDTO";
import type { ConnectionDTO } from "./ConnectionDTO";
import type { ValidationIssueDTO } from "./ValidationIssueDTO";

// Response of GET and PUT /graph: the revision, the components, their connections and contract issues.
export interface ScenarioGraphDTO {
  revision: number;
  components: ComponentDTO[];
  connections: ConnectionDTO[];
  issues: ValidationIssueDTO[];
}
