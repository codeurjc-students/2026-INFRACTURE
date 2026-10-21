import { apiFetch } from "~/shared/api/api-fetch";
import type { PageDTO } from "~/shared/model/PageDTO";
import type { ComponentTemplateDTO } from "../model/ComponentTemplateDTO";

const API_PATH = "/api/v1/component-templates";

export function getComponentTemplates(
  page = 0,
): Promise<PageDTO<ComponentTemplateDTO>> {
  return apiFetch<PageDTO<ComponentTemplateDTO>>(`${API_PATH}?page=${page}`);
}
