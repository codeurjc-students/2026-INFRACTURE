import type { Route } from "./+types/project";
import { LockIcon, PlusIcon } from "lucide-react";
import { Link } from "react-router";
import PagedList from "~/components/paged-list";
import { buttonVariants } from "~/components/ui/button";
import {
  getProject,
  updateProject,
} from "~/features/projects/api/project-service";
import { getScenarios } from "~/features/projects/api/scenario-service";
import DetailsPage, {
  DetailsError,
  loadOrNotFound,
} from "~/features/projects/components/details-page";
import EditableDetails from "~/features/projects/components/editable-details";
import ProjectsBreadcrumb from "~/features/projects/components/projects-breadcrumb";
import ScenarioCard from "~/features/projects/components/scenario-card";
import {
  detailsErrorMessage,
  formatDate,
  formatDateTime,
  readDetailsForm,
} from "~/features/projects/details-form-data";

export function meta({ loaderData }: Route.MetaArgs) {
  return [{ title: `${loaderData?.project.name ?? "Project"} | Infracture` }];
}

export async function clientLoader({ params }: Route.ClientLoaderArgs) {
  const [project, scenarios] = await loadOrNotFound(
    Promise.all([getProject(params.projectId), getScenarios(params.projectId)]),
  );
  return { project, scenarios };
}

export async function clientAction({
  request,
  params,
}: Route.ClientActionArgs) {
  const { details, nameError } = readDetailsForm(
    await request.formData(),
    "project",
  );
  if (nameError) {
    return { nameError };
  }
  try {
    await updateProject(params.projectId, details);
    return { saved: true as const };
  } catch (error) {
    return { error: detailsErrorMessage(error) };
  }
}

export function ErrorBoundary() {
  return <DetailsError subject="Project" />;
}

const newScenarioLink = buttonVariants({
  size: "lg",
  className: "h-11 w-full px-4 sm:h-10 sm:w-auto",
});

export default function ProjectRoute({
  loaderData,
}: Pick<Route.ComponentProps, "loaderData">) {
  const { project, scenarios } = loaderData;
  const newScenarioPath = `/projects/${project.id}/scenarios/new`;

  return (
    <DetailsPage breadcrumb={<ProjectsBreadcrumb current={project.name} />}>
      <EditableDetails
        name={project.name}
        description={project.description}
        namePlaceholder="Cache lab"
        meta={
          <>
            <span className="inline-flex items-center gap-1.5">
              <LockIcon aria-hidden="true" className="size-3.5" />
              Private
            </span>
            <span>Created {formatDate(project.createdAt)}</span>
            <span>Last edited {formatDateTime(project.updatedAt)}</span>
          </>
        }
      />
      <section aria-labelledby="scenarios-heading" className="pt-6">
        <div className="flex flex-wrap items-end justify-between gap-4 pb-4">
          <h2 id="scenarios-heading" className="type-page-title">
            Scenarios
          </h2>
          {scenarios.page.totalElements > 0 && (
            <Link to={newScenarioPath} className={newScenarioLink}>
              <PlusIcon data-icon="inline-start" />
              New scenario
            </Link>
          )}
        </div>
        {scenarios.page.totalElements === 0 ? (
          <div className="flex flex-col items-start gap-3.5 rounded-xl border bg-card p-5 sm:p-8">
            <h3 className="type-title">No scenarios yet</h3>
            <p className="max-w-[52ch] type-body text-muted-foreground">
              Each scenario is an independent experiment. Start with an empty
              one and design its graph.
            </p>
            <Link to={newScenarioPath} className={newScenarioLink}>
              <PlusIcon data-icon="inline-start" />
              Create your first scenario
            </Link>
          </div>
        ) : (
          <PagedList
            key={project.id}
            initialPage={scenarios}
            loadPage={(page) => getScenarios(String(project.id), page)}
            noun="scenarios"
            renderItem={(scenario) => (
              <ScenarioCard projectId={project.id} scenario={scenario} />
            )}
          />
        )}
      </section>
    </DetailsPage>
  );
}
