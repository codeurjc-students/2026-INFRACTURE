import type { ConnectionDTO } from "./ConnectionDTO";
import type { SaveComponentDTO } from "./SaveComponentDTO";
import type { WorkloadDTO } from "./WorkloadDTO";

// Body of PUT /graph: the whole graph plus the revision the editor started from.
export interface SaveScenarioGraphDTO {
  revision: number;
  components: SaveComponentDTO[];
  connections: ConnectionDTO[];
  // null removes the load.
  workload: WorkloadDTO | null;
}
