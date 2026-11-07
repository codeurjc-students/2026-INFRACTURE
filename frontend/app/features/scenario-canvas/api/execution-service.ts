import { apiFetch } from "~/shared/api/api-fetch";
import type { ExecutionDTO } from "../model/ExecutionDTO";

// URL of a scenario's executions. Ids are encoded because they come from the route.
function executionsPath(projectId: string, scenarioId: string) {
  return `/api/v1/projects/${encodeURIComponent(projectId)}/scenarios/${encodeURIComponent(scenarioId)}/executions`;
}

export function runScenario(
  projectId: string,
  scenarioId: string,
  revision: number,
): Promise<ExecutionDTO> {
  return apiFetch<ExecutionDTO>(executionsPath(projectId, scenarioId), {
    method: "POST",
    body: JSON.stringify({ revision }),
  });
}

export async function getCurrentExecution(
  projectId: string,
  scenarioId: string,
): Promise<ExecutionDTO | null> {
  const execution = await apiFetch<ExecutionDTO | undefined>(
    `${executionsPath(projectId, scenarioId)}/current`,
  );
  return execution ?? null;
}

export function stopExecution(
  projectId: string,
  scenarioId: string,
  executionId: number,
): Promise<ExecutionDTO> {
  return apiFetch<ExecutionDTO>(
    `${executionsPath(projectId, scenarioId)}/${executionId}/stop`,
    { method: "POST" },
  );
}
