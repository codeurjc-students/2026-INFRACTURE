import { Link } from "react-router";

const linkClass =
  "active:text-foreground pointer-fine:hover:text-foreground motion-safe:transition-colors";

// Way back to the list, and to the project when inside one.
export default function ProjectsBreadcrumb({
  project,
  current,
}: {
  project?: { id: number; name: string };
  current: string;
}) {
  return (
    <nav aria-label="Breadcrumb" className="min-w-0">
      <ol className="flex min-w-0 items-center gap-2 type-small text-muted-foreground">
        <li>
          <Link to="/projects" className={linkClass}>
            Projects
          </Link>
        </li>
        <li aria-hidden="true">/</li>
        {project && (
          <>
            <li className="min-w-0 truncate">
              <Link to={`/projects/${project.id}`} className={linkClass}>
                {project.name}
              </Link>
            </li>
            <li aria-hidden="true">/</li>
          </>
        )}
        <li aria-current="page" className="truncate text-foreground">
          {current}
        </li>
      </ol>
    </nav>
  );
}
