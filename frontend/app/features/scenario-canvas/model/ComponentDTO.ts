import type { PositionDTO } from "./PositionDTO";

// One node of the graph as the backend sends it. The key is a UUID created by the browser.
export interface ComponentDTO {
  key: string;
  templateKey: string;
  type: string;
  name: string;
  position: PositionDTO;
  profile: string | null;
  parameters: Record<string, number>;
}
