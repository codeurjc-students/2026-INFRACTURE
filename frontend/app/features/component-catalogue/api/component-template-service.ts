import type { ComponentTemplateDTO } from "../model/ComponentTemplateDTO";

const API_URL = "/api/v1/component-templates";

export async function getComponentTemplates(): Promise<ComponentTemplateDTO[]> {
  let response: Response;

  try {
    response = await fetch(API_URL);
  } catch (cause) {
    throw new Error("Could not connect to the component catalogue API", {
      cause,
    });
  }

  if (!response.ok) {
    throw new Error("Failed to fetch component templates");
  }

  return response.json();
}
