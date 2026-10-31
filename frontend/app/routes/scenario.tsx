import type { Route } from "./+types/scenario";
import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";
import { getProject } from "~/features/projects/api/project-service";
import {
  getScenario,
  updateScenario,
} from "~/features/projects/api/scenario-service";
import {
  DetailsError,
  loadOrNotFound,
} from "~/features/projects/components/details-page";
import { getGraph } from "~/features/scenario-canvas/api/graph-service";
import ScenarioCanvas from "~/features/scenario-canvas/components/scenario-canvas";
import {
  detailsErrorMessage,
  readDetailsForm,
} from "~/features/projects/details-form-data";

export function meta({ loaderData }: Route.MetaArgs) {
  return [{ title: `${loaderData?.scenario.name ?? "Scenario"} | Infracture` }];
}

// Loads everything the editor needs at once. A missing or foreign scenario becomes the 404 page.
export async function clientLoader({ params }: Route.ClientLoaderArgs) {
  const [project, scenario, graph, templates] = await loadOrNotFound(
    Promise.all([
      getProject(params.projectId),
      getScenario(params.projectId, params.scenarioId),
      getGraph(params.projectId, params.scenarioId),
      // The catalogue is small; one page of the maximum size holds every template.
      getComponentTemplates(0, 100),
    ]),
  );
  return { project, scenario, graph, templates: templates.content };
}

// Saves the scenario name and description from the Details panel.
export async function clientAction({
  request,
  params,
}: Route.ClientActionArgs) {
  const { details, nameError } = readDetailsForm(
    await request.formData(),
    "scenario",
  );
  if (nameError) {
    return { nameError };
  }
  try {
    await updateScenario(params.projectId, params.scenarioId, details);
    return { saved: true as const };
  } catch (error) {
    return { error: detailsErrorMessage(error) };
  }
}

export function ErrorBoundary() {
  return <DetailsError subject="Scenario" />;
}

export default function ScenarioRoute({
  loaderData,
}: Pick<Route.ComponentProps, "loaderData">) {
  const { project, scenario, graph, templates } = loaderData;

  return (
    // The key restarts the editor when another scenario opens.
    <ScenarioCanvas
      key={scenario.id}
      project={project}
      scenario={scenario}
      graph={graph}
      templates={templates}
    />
  );
}
