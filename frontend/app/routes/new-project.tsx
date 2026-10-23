import type { Route } from "./+types/new-project";
import { useState } from "react";
import { Form, Link, redirect, useNavigation } from "react-router";
import AppHeader from "~/components/app-header";
import { buttonVariants } from "~/components/ui/button";
import { createProject } from "~/features/projects/api/project-service";
import DetailsForm from "~/features/projects/components/details-form";
import ProjectsBreadcrumb from "~/features/projects/components/projects-breadcrumb";
import {
  detailsErrorMessage,
  readDetailsForm,
} from "~/features/projects/details-form-data";

export function meta() {
  return [{ title: "New project | Infracture" }];
}

export async function clientAction({ request }: Route.ClientActionArgs) {
  const { details, nameError } = readDetailsForm(
    await request.formData(),
    "project",
  );
  if (nameError) {
    return { nameError };
  }
  try {
    const created = await createProject(details);
    return redirect(`/projects/${created.id}`);
  } catch (error) {
    return { error: detailsErrorMessage(error) };
  }
}

export default function NewProjectRoute({
  actionData,
}: Pick<Route.ComponentProps, "actionData">) {
  const navigation = useNavigation();
  const [name, setName] = useState("");
  const title = name.trim();

  return (
    <div className="min-h-svh">
      <AppHeader />
      <div className="mx-auto max-w-7xl px-4 pb-20 sm:px-6">
        <main className="flex max-w-3xl flex-col gap-6 pt-8">
          <div className="flex flex-col gap-4">
            <ProjectsBreadcrumb current="New project" />
            <h1
              className={`type-headline ${title ? "" : "text-muted-foreground"}`}
            >
              {title || "New project"}
            </h1>
            <p className="type-title text-muted-foreground">
              It starts private. Only you can see it.
            </p>
          </div>
          <Form method="post" noValidate>
            <DetailsForm
              {...actionData}
              namePlaceholder="Cache lab"
              errorTitle="Could not create the project"
              submitLabel="Create project"
              pendingLabel="Creating…"
              pending={navigation.state === "submitting"}
              onNameChange={setName}
              cancel={
                <Link
                  to="/projects"
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
        </main>
      </div>
    </div>
  );
}
