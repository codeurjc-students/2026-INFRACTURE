import { apiFetch } from "~/shared/api/api-fetch";
import type { SaveScenarioGraphDTO } from "../model/SaveScenarioGraphDTO";
import type { ScenarioGraphDTO } from "../model/ScenarioGraphDTO";

// URL of a scenario's graph. Ids are encoded because they come from the route.
function graphPath(projectId: string, scenarioId: string) {
  return `/api/v1/projects/${encodeURIComponent(projectId)}/scenarios/${encodeURIComponent(scenarioId)}/graph`;
}

// Reads the graph: revision, components and contract issues.
export function getGraph(
  projectId: string,
  scenarioId: string,
): Promise<ScenarioGraphDTO> {
  return apiFetch<ScenarioGraphDTO>(graphPath(projectId, scenarioId));
}

// Saves the whole graph with the expected revision. A stale revision is rejected with 409.
export function saveGraph(
  projectId: string,
  scenarioId: string,
  graph: SaveScenarioGraphDTO,
): Promise<ScenarioGraphDTO> {
  return apiFetch<ScenarioGraphDTO>(graphPath(projectId, scenarioId), {
    method: "PUT",
    body: JSON.stringify(graph),
  });
}
