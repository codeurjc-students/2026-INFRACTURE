import type { Route } from "./+types/scenario";
import { BlocksIcon } from "lucide-react";
import { Link } from "react-router";
import { buttonVariants } from "~/components/ui/button";
import { getProject } from "~/features/projects/api/project-service";
import {
  getScenario,
  updateScenario,
} from "~/features/projects/api/scenario-service";
import DetailsPage, {
  DetailsError,
  loadOrNotFound,
} from "~/features/projects/components/details-page";
import EditableDetails from "~/features/projects/components/editable-details";
import ProjectsBreadcrumb from "~/features/projects/components/projects-breadcrumb";
import {
  detailsErrorMessage,
  formatDate,
  formatDateTime,
  readDetailsForm,
} from "~/features/projects/details-form-data";

export function meta({ loaderData }: Route.MetaArgs) {
  return [{ title: `${loaderData?.scenario.name ?? "Scenario"} | Infracture` }];
}

export async function clientLoader({ params }: Route.ClientLoaderArgs) {
  const [project, scenario] = await loadOrNotFound(
    Promise.all([
      getProject(params.projectId),
      getScenario(params.projectId, params.scenarioId),
    ]),
  );
  return { project, scenario };
}

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
  const { project, scenario } = loaderData;

  return (
    <DetailsPage
      breadcrumb={
        <ProjectsBreadcrumb project={project} current={scenario.name} />
      }
    >
      <EditableDetails
        name={scenario.name}
        description={scenario.description}
        namePlaceholder="Cold cache"
        meta={
          <>
            <span>Revision {scenario.revision}</span>
            <span>Created {formatDate(scenario.createdAt)}</span>
            <span>Last edited {formatDateTime(scenario.updatedAt)}</span>
          </>
        }
      />
      <section
        aria-labelledby="graph-heading"
        className="flex flex-col items-start gap-3.5 rounded-xl border border-dashed bg-card p-5 sm:p-8"
      >
        <h2 id="graph-heading" className="type-page-title">
          No components yet
        </h2>
        <p className="max-w-[52ch] type-body text-muted-foreground">
          This scenario is empty. Design its graph next: choose components from
          the catalogue and connect them.
        </p>
        <Link
          to="/catalogue"
          className={buttonVariants({
            variant: "outline",
            size: "lg",
            className: "h-11 w-full px-4 sm:h-10 sm:w-auto",
          })}
        >
          <BlocksIcon data-icon="inline-start" />
          Browse components
        </Link>
      </section>
    </DetailsPage>
  );
}
