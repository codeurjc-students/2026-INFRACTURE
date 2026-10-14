import type { PageDTO } from "~/shared/model/PageDTO";
import type { ComponentTemplateDTO } from "../model/ComponentTemplateDTO";

const API_PATH = "/api/v1/component-templates";

export async function getComponentTemplates(page = 0): Promise<
  PageDTO<ComponentTemplateDTO>
> {
  const response = await fetch(`${API_PATH}?page=${page}`);

  if (!response.ok) {
    throw new Error("Failed to fetch component templates");
  }

  return response.json();
}
