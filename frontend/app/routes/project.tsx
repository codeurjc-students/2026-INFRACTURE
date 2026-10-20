import type { Route } from "./+types/project";
import { CircleCheckIcon, LockIcon, PencilIcon } from "lucide-react";
import { useState } from "react";
import {
  data,
  isRouteErrorResponse,
  Link,
  useFetcher,
  useRevalidator,
  useRouteError,
} from "react-router";
import AppHeader from "~/components/app-header";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button, buttonVariants } from "~/components/ui/button";
import {
  getProject,
  updateProject,
} from "~/features/projects/api/project-service";
import ProjectForm from "~/features/projects/components/project-form";
import ProjectsBreadcrumb from "~/features/projects/components/projects-breadcrumb";
import {
  formatDate,
  formatDateTime,
  projectErrorMessage,
  readProjectForm,
} from "~/features/projects/project-form-data";
import { HttpError } from "~/shared/api/api-fetch";

export function meta({ loaderData }: Route.MetaArgs) {
  return [{ title: `${loaderData?.project.name ?? "Project"} | Infracture` }];
}

export async function clientLoader({ params }: Route.ClientLoaderArgs) {
  try {
    return { project: await getProject(params.projectId) };
  } catch (error) {
    if (
      error instanceof HttpError &&
      (error.status === 404 || error.status === 400)
    ) {
      throw data(null, { status: 404 });
    }
    throw error;
  }
}

export async function clientAction({
  request,
  params,
}: Route.ClientActionArgs) {
  const { project, nameError } = readProjectForm(await request.formData());
  if (nameError) {
    return { nameError };
  }
  try {
    await updateProject(params.projectId, project);
    return { saved: true };
  } catch (error) {
    return { error: projectErrorMessage(error) };
  }
}

function ProjectPage({
  current,
  children,
}: {
  current?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-svh">
      <AppHeader />
      <div className="mx-auto max-w-7xl px-4 pb-20 sm:px-6">
        <main className="flex flex-col gap-6 pt-8">
          {current && <ProjectsBreadcrumb current={current} />}
          {children}
        </main>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  const error = useRouteError();
  const revalidator = useRevalidator();

  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <ProjectPage>
        <div className="flex max-w-3xl flex-col items-start gap-6">
          <h1 className="type-headline">Project not found</h1>
          <p className="type-title text-muted-foreground">
            It does not exist, or it belongs to another account.
          </p>
          <Link
            to="/projects"
            className={buttonVariants({ size: "lg", className: "px-4" })}
          >
            Go to your projects
          </Link>
        </div>
      </ProjectPage>
    );
  }

  return (
    <ProjectPage>
      <section className="flex max-w-3xl flex-col items-start gap-4 rounded-xl border bg-card p-5 sm:p-8">
        <h1 className="type-page-title">Project unavailable</h1>
        <p role="alert" className="type-body text-muted-foreground">
          This project could not be loaded. Try again in a moment.
        </p>
        <Button
          type="button"
          size="lg"
          className="px-4"
          onClick={() => revalidator.revalidate()}
          disabled={revalidator.state === "loading"}
        >
          {revalidator.state === "loading" ? "Loading…" : "Try again"}
        </Button>
      </section>
    </ProjectPage>
  );
}

export default function ProjectRoute({
  loaderData,
}: Pick<Route.ComponentProps, "loaderData">) {
  const { project } = loaderData;
  const fetcher = useFetcher<typeof clientAction>();
  const [editing, setEditing] = useState(false);
  const [draftName, setDraftName] = useState(project.name);
  const [result, setResult] = useState(fetcher.data);

  // Close the form once a save succeeds; the loader then brings the saved project.
  if (fetcher.data !== result) {
    setResult(fetcher.data);
    if (fetcher.data && "saved" in fetcher.data) {
      setEditing(false);
    }
  }

  const saved = !editing && result !== undefined && "saved" in result;
  const title = editing ? draftName.trim() || project.name : project.name;

  function startEditing() {
    setDraftName(project.name);
    setResult(undefined);
    setEditing(true);
  }

  return (
    <ProjectPage current={project.name}>
      {saved && (
        <Alert role="status" className="enter-soft max-w-3xl">
          <CircleCheckIcon className="text-healthy-text" />
          <AlertTitle>Changes saved</AlertTitle>
          <AlertDescription>
            The name and description are up to date.
          </AlertDescription>
        </Alert>
      )}
      <div className="flex flex-wrap items-end justify-between gap-x-10 gap-y-5">
        <div className="flex min-w-0 flex-[1_1_560px] flex-col gap-3.5">
          <h1 className="type-headline">{title}</h1>
          {!editing && project.description && (
            <p className="max-w-[52ch] type-title text-muted-foreground">
              {project.description}
            </p>
          )}
          <div className="flex flex-wrap gap-x-5 gap-y-1.5 type-small text-muted-foreground">
            <span className="inline-flex items-center gap-1.5">
              <LockIcon aria-hidden="true" className="size-3.5" />
              Private
            </span>
            <span>Created {formatDate(project.createdAt)}</span>
            <span>Last edited {formatDateTime(project.updatedAt)}</span>
          </div>
        </div>
        {!editing && (
          <Button
            type="button"
            variant="outline"
            size="lg"
            onClick={startEditing}
            className="h-11 w-full px-4 sm:h-10 sm:w-auto"
          >
            <PencilIcon data-icon="inline-start" />
            Edit details
          </Button>
        )}
      </div>
      {editing && (
        <fetcher.Form method="post" noValidate className="max-w-3xl">
          <ProjectForm
            {...(result && !("saved" in result) ? result : {})}
            defaultName={project.name}
            defaultDescription={project.description ?? ""}
            errorTitle="Could not save the changes"
            submitLabel="Save changes"
            pendingLabel="Saving…"
            pending={fetcher.state !== "idle"}
            onNameChange={setDraftName}
            cancel={
              <Button
                type="button"
                variant="ghost"
                size="lg"
                onClick={() => setEditing(false)}
                className="px-4 text-muted-foreground"
              >
                Cancel
              </Button>
            }
          />
        </fetcher.Form>
      )}
    </ProjectPage>
  );
}
