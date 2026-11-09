import type { Route } from "./+types/scenario";
import { useRouteLoaderData } from "react-router";
import { getComponentTemplates } from "~/features/component-catalogue/api/component-template-service";
import { getWorkloadProfiles } from "~/features/component-catalogue/api/workload-profile-service";
import { getProject } from "~/features/projects/api/project-service";
import {
  getScenario,
  updateScenario,
} from "~/features/projects/api/scenario-service";
import {
  DetailsError,
  loadOrNotFound,
} from "~/features/projects/components/details-page";
import { getCurrentExecution } from "~/features/scenario-canvas/api/execution-service";
import { getGraph } from "~/features/scenario-canvas/api/graph-service";
import ScenarioCanvas from "~/features/scenario-canvas/components/scenario-canvas";
import type { clientLoader as privateLoader } from "~/routes/private-layout";
import {
  detailsErrorMessage,
  readDetailsForm,
} from "~/features/projects/details-form-data";

export function meta({ loaderData }: Route.MetaArgs) {
  return [{ title: `${loaderData?.scenario.name ?? "Scenario"} | Infracture` }];
}

// Loads everything the editor needs at once. A missing or foreign scenario becomes the 404 page.
export async function clientLoader({ params }: Route.ClientLoaderArgs) {
  const [project, scenario, graph, templates, workloadProfiles, execution] =
    await loadOrNotFound(
      Promise.all([
        getProject(params.projectId),
        getScenario(params.projectId, params.scenarioId),
        getGraph(params.projectId, params.scenarioId),
        // The catalogue is small; one page of the maximum size holds every template.
        getComponentTemplates(0, 100),
        getWorkloadProfiles(),
        getCurrentExecution(params.projectId, params.scenarioId),
      ]),
    );
  return {
    project,
    scenario,
    graph,
    templates: templates.content,
    workloadProfiles,
    execution,
  };
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
  const { project, scenario, graph, templates, workloadProfiles, execution } =
    loaderData;
  // The private layout already loaded the signed-in account; its id keeps local drafts per account.
  const user = useRouteLoaderData<typeof privateLoader>(
    "routes/private-layout",
  )?.user;

  return (
    // The key restarts the editor when another scenario opens.
    <ScenarioCanvas
      key={scenario.id}
      project={project}
      scenario={scenario}
      graph={graph}
      templates={templates}
      workloadProfiles={workloadProfiles}
      execution={execution}
      userId={user?.id}
    />
  );
}
