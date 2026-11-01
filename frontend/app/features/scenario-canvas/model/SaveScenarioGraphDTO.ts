import type { SaveComponentDTO } from "./SaveComponentDTO";

// Body of PUT /graph: the whole graph plus the revision the editor started from.
export interface SaveScenarioGraphDTO {
  revision: number;
  components: SaveComponentDTO[];
}
