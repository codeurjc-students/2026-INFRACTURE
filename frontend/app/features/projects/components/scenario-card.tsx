import { Link } from "react-router";
import { formatUpdated } from "../details-form-data";
import type { ScenarioDTO } from "../model/ScenarioDTO";

export default function ScenarioCard({
  projectId,
  scenario,
}: {
  projectId: number;
  scenario: ScenarioDTO;
}) {
  return (
    <Link
      to={`/projects/${projectId}/scenarios/${scenario.id}`}
      className="flex h-full flex-col gap-2 rounded-xl border bg-card p-4 text-foreground no-underline active:bg-accent pointer-fine:hover:bg-accent motion-safe:transition-[background-color,scale] motion-safe:duration-150 motion-safe:active:scale-[0.98]"
    >
      <h3 className="type-page-title [overflow-wrap:anywhere]">
        {scenario.name}
      </h3>
      {scenario.description && (
        <p className="line-clamp-2 type-body text-muted-foreground">
          {scenario.description}
        </p>
      )}
      <span className="mt-auto pt-2 type-caption text-muted-foreground">
        Updated {formatUpdated(scenario.updatedAt)}
      </span>
    </Link>
  );
}
