import type { Route } from "./+types/projects";
import { PlusIcon } from "lucide-react";
import { Link, useRevalidator } from "react-router";
import AppHeader from "~/components/app-header";
import { Button, buttonVariants } from "~/components/ui/button";
import { getProjects } from "~/features/projects/api/project-service";
import ProjectList from "~/features/projects/components/project-list";

export function meta() {
  return [{ title: "Projects | Infracture" }];
}

export async function clientLoader() {
  return { projects: await getProjects() };
}

function ProjectsPage({
  children,
  aside,
}: {
  children: React.ReactNode;
  aside?: React.ReactNode;
}) {
  return (
    <div className="min-h-svh">
      <AppHeader />
      <div className="mx-auto max-w-7xl px-4 pb-20 sm:px-6">
        <main>
          <section className="flex flex-wrap items-end justify-between gap-x-12 gap-y-6 pt-8 pb-7 sm:pt-14 sm:pb-10">
            <div className="@container flex min-w-0 flex-[1_1_520px] flex-col gap-4">
              <h1 className="type-hero">Projects</h1>
              <p className="max-w-[52ch] type-title text-muted-foreground">
                Each project holds the scenarios you build, run and break. Only
                you can see them.
              </p>
            </div>
            {aside}
          </section>
          {children}
        </main>
      </div>
    </div>
  );
}

const newProjectLink = buttonVariants({
  size: "lg",
  className: "h-11 w-full px-4 sm:h-10 sm:w-auto",
});

export function ErrorBoundary() {
  const revalidator = useRevalidator();

  return (
    <ProjectsPage>
      <section className="flex flex-col items-start gap-4 rounded-xl border bg-card p-5 sm:p-8">
        <h2 className="type-page-title">Projects unavailable</h2>
        <p
          role="alert"
          className="max-w-[52ch] type-body text-muted-foreground"
        >
          Your projects could not be loaded. Try again in a moment.
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
    </ProjectsPage>
  );
}

export default function ProjectsRoute({
  loaderData,
}: Pick<Route.ComponentProps, "loaderData">) {
  const { projects } = loaderData;
  const total = projects.page.totalElements;

  if (total === 0) {
    return (
      <ProjectsPage>
        <section className="flex flex-col items-start gap-3.5 rounded-xl border bg-card p-5 sm:p-8">
          <h2 className="type-page-title">Nothing here to break yet</h2>
          <p className="max-w-[52ch] type-body text-muted-foreground">
            Create a project, then add the scenarios you want to build and put
            under pressure.
          </p>
          <Link to="/projects/new" className={newProjectLink}>
            <PlusIcon data-icon="inline-start" />
            Create your first project
          </Link>
        </section>
      </ProjectsPage>
    );
  }

  return (
    <ProjectsPage
      aside={
        <div className="flex w-full flex-col items-stretch gap-3 sm:w-auto sm:items-end">
          <Link to="/projects/new" className={newProjectLink}>
            <PlusIcon data-icon="inline-start" />
            New project
          </Link>
          <p className="type-small text-muted-foreground tabular-nums">
            {total === 1 ? "1 project" : `${total} projects`}, all private
          </p>
        </div>
      }
    >
      <ProjectList initialPage={projects} />
    </ProjectsPage>
  );
}
