import { CircleAlertIcon } from "lucide-react";
import { useState } from "react";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { Spinner } from "~/components/ui/spinner";
import type { PageDTO } from "~/shared/model/PageDTO";
import { getProjects } from "../api/project-service";
import type { ProjectDTO } from "../model/ProjectDTO";
import ProjectCard from "./project-card";

export default function ProjectList({
  initialPage,
}: {
  initialPage: PageDTO<ProjectDTO>;
}) {
  const [projects, setProjects] = useState(initialPage);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const hasMore = projects.page.number + 1 < projects.page.totalPages;

  async function loadMore() {
    setLoading(true);
    setError(false);
    try {
      const nextPage = await getProjects(projects.page.number + 1);
      setProjects((current) => {
        // A project created meanwhile shifts the pages, so skip the ones already shown.
        const shown = new Set(current.content.map((project) => project.id));
        return {
          ...nextPage,
          content: [
            ...current.content,
            ...nextPage.content.filter((project) => !shown.has(project.id)),
          ],
        };
      });
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,340px),1fr))] gap-2">
        {projects.content.map((project) => (
          <li key={project.id}>
            <ProjectCard project={project} />
          </li>
        ))}
      </ul>
      {error && (
        <Alert variant="destructive" className="enter-soft mt-6 max-w-xl">
          <CircleAlertIcon />
          <AlertTitle>Could not load more projects</AlertTitle>
          <AlertDescription>
            The projects already shown are still here. Try again.
          </AlertDescription>
        </Alert>
      )}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-3 pt-6">
        {hasMore && (
          <Button
            type="button"
            variant="outline"
            size="lg"
            onClick={loadMore}
            disabled={loading}
            className="w-full px-4 sm:w-auto"
          >
            {loading && <Spinner data-icon="inline-start" />}
            {loading ? "Loading…" : error ? "Try again" : "More results"}
          </Button>
        )}
        <p
          role="status"
          className="type-small text-muted-foreground tabular-nums"
        >
          {loading
            ? "Loading more projects…"
            : `Showing ${projects.content.length} of ${projects.page.totalElements}`}
        </p>
      </div>
    </>
  );
}
