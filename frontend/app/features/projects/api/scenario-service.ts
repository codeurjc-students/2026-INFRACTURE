import { apiFetch } from "~/shared/api/api-fetch";
import type { PageDTO } from "~/shared/model/PageDTO";
import type { SaveScenarioDTO } from "../model/SaveScenarioDTO";
import type { ScenarioDTO } from "../model/ScenarioDTO";

function scenariosPath(projectId: string) {
  return `/api/v1/projects/${encodeURIComponent(projectId)}/scenarios`;
}

export function getScenarios(
  projectId: string,
  page = 0,
): Promise<PageDTO<ScenarioDTO>> {
  return apiFetch<PageDTO<ScenarioDTO>>(
    `${scenariosPath(projectId)}?page=${page}`,
  );
}

export function getScenario(
  projectId: string,
  id: string,
): Promise<ScenarioDTO> {
  return apiFetch<ScenarioDTO>(
    `${scenariosPath(projectId)}/${encodeURIComponent(id)}`,
  );
}

export function createScenario(
  projectId: string,
  scenario: SaveScenarioDTO,
): Promise<ScenarioDTO> {
  return apiFetch<ScenarioDTO>(scenariosPath(projectId), {
    method: "POST",
    body: JSON.stringify(scenario),
  });
}

export function updateScenario(
  projectId: string,
  id: string,
  scenario: SaveScenarioDTO,
): Promise<ScenarioDTO> {
  return apiFetch<ScenarioDTO>(
    `${scenariosPath(projectId)}/${encodeURIComponent(id)}`,
    { method: "PUT", body: JSON.stringify(scenario) },
  );
}
