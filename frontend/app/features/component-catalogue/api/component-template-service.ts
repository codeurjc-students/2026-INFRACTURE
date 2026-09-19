import type { ComponentTemplateDTO } from "../model/ComponentTemplateDTO";

const API_PATH = "/api/v1/component-templates";

export async function getComponentTemplates(): Promise<ComponentTemplateDTO[]> {
  let response: Response;

  try {
    response = await fetch(new URL(API_PATH, window.location.origin));
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
