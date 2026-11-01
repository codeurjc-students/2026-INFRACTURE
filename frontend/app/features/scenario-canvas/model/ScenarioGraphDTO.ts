import type { ComponentDTO } from "./ComponentDTO";
import type { ValidationIssueDTO } from "./ValidationIssueDTO";

// Response of GET and PUT /graph: the revision, the components and their contract issues.
export interface ScenarioGraphDTO {
  revision: number;
  components: ComponentDTO[];
  issues: ValidationIssueDTO[];
}
