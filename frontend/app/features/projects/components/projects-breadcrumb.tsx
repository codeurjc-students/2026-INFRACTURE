import { Link } from "react-router";

// Way back to the list from a project page.
export default function ProjectsBreadcrumb({ current }: { current: string }) {
  return (
    <nav aria-label="Breadcrumb" className="min-w-0">
      <ol className="flex min-w-0 items-center gap-2 type-small text-muted-foreground">
        <li>
          <Link
            to="/projects"
            className="active:text-foreground pointer-fine:hover:text-foreground motion-safe:transition-colors"
          >
            Projects
          </Link>
        </li>
        <li aria-hidden="true">/</li>
        <li aria-current="page" className="truncate text-foreground">
          {current}
        </li>
      </ol>
    </nav>
  );
}
