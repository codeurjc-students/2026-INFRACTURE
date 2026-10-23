import type { Route } from "./+types/new-scenario";
import { useState } from "react";
import { Form, Link, redirect, useNavigation } from "react-router";
import { buttonVariants } from "~/components/ui/button";
import { getProject } from "~/features/projects/api/project-service";
import { createScenario } from "~/features/projects/api/scenario-service";
import DetailsForm from "~/features/projects/components/details-form";
import DetailsPage, {
  DetailsError,
  loadOrNotFound,
} from "~/features/projects/components/details-page";
import ProjectsBreadcrumb from "~/features/projects/components/projects-breadcrumb";
import {
  detailsErrorMessage,
  readDetailsForm,
} from "~/features/projects/details-form-data";

export function meta() {
  return [{ title: "New scenario | Infracture" }];
}

export async function clientLoader({ params }: Route.ClientLoaderArgs) {
  return { project: await loadOrNotFound(getProject(params.projectId)) };
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
    const created = await createScenario(params.projectId, details);
    return redirect(`/projects/${params.projectId}/scenarios/${created.id}`);
  } catch (error) {
    return { error: detailsErrorMessage(error) };
  }
}

export function ErrorBoundary() {
  return <DetailsError subject="Project" />;
}

export default function NewScenarioRoute({
  loaderData,
  actionData,
}: Pick<Route.ComponentProps, "loaderData" | "actionData">) {
  const { project } = loaderData;
  const navigation = useNavigation();
  const [name, setName] = useState("");
  const title = name.trim();

  return (
    <DetailsPage
      breadcrumb={
        <ProjectsBreadcrumb project={project} current="New scenario" />
      }
    >
      <div className="flex max-w-3xl flex-col gap-4">
        <h1 className={`type-headline ${title ? "" : "text-muted-foreground"}`}>
          {title || "New scenario"}
        </h1>
        <p className="type-title text-muted-foreground">
          It starts empty. You design its graph next.
        </p>
      </div>
      <Form method="post" noValidate className="max-w-3xl">
        <DetailsForm
          {...actionData}
          namePlaceholder="Cold cache"
          errorTitle="Could not create the scenario"
          submitLabel="Create scenario"
          pendingLabel="Creating…"
          pending={navigation.state === "submitting"}
          onNameChange={setName}
          cancel={
            <Link
              to={`/projects/${project.id}`}
              className={buttonVariants({
                variant: "ghost",
                size: "lg",
                className: "px-4 text-muted-foreground",
              })}
            >
              Cancel
            </Link>
          }
        />
      </Form>
    </DetailsPage>
  );
}
