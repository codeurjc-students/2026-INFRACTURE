import { apiFetch } from "~/shared/api/api-fetch";
import type { PageDTO } from "~/shared/model/PageDTO";
import type { ProjectDTO } from "../model/ProjectDTO";
import type { SaveProjectDTO } from "../model/SaveProjectDTO";

const API_PATH = "/api/v1/projects";

export function getProjects(page = 0): Promise<PageDTO<ProjectDTO>> {
  return apiFetch<PageDTO<ProjectDTO>>(`${API_PATH}?page=${page}`);
}

export function getProject(id: string): Promise<ProjectDTO> {
  return apiFetch<ProjectDTO>(`${API_PATH}/${encodeURIComponent(id)}`);
}

export function createProject(project: SaveProjectDTO): Promise<ProjectDTO> {
  return apiFetch<ProjectDTO>(API_PATH, {
    method: "POST",
    body: JSON.stringify(project),
  });
}

export function updateProject(
  id: string,
  project: SaveProjectDTO,
): Promise<ProjectDTO> {
  return apiFetch<ProjectDTO>(`${API_PATH}/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(project),
  });
}
