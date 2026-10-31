import { apiFetch } from "~/shared/api/api-fetch";
import type { PageDTO } from "~/shared/model/PageDTO";
import type { ComponentTemplateDTO } from "../model/ComponentTemplateDTO";

const API_PATH = "/api/v1/component-templates";

// One page of the catalogue. The editor passes a size to get every template in one request.
export function getComponentTemplates(
  page = 0,
  size?: number,
): Promise<PageDTO<ComponentTemplateDTO>> {
  const query = size === undefined ? "" : `&size=${size}`;
  return apiFetch<PageDTO<ComponentTemplateDTO>>(
    `${API_PATH}?page=${page}${query}`,
  );
}
