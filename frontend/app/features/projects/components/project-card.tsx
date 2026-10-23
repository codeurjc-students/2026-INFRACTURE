import { LockIcon, NetworkIcon, PauseIcon } from "lucide-react";
import { Link } from "react-router";
import type { ProjectDTO } from "../model/ProjectDTO";
import { formatUpdated } from "../details-form-data";

// Sample run histories until executions exist (#86): h healthy, d degraded, f failed, r recovering.
// An empty one means the project has not run yet.
const SAMPLE_HISTORIES = [
  "",
  "hhhhdhhhdhfhhdff",
  "hhhhhhhdhhhhhhdr",
  "hhhhhhhhhhhhhhhh",
];
const HISTORY_LENGTH = 16;
const RUN_CLASS: Record<string, string> = {
  h: "bg-accent",
  d: "bg-degraded-fill",
  f: "bg-failed-fill",
  r: "bg-recovering-text",
};

export default function ProjectCard({ project }: { project: ProjectDTO }) {
  const history = SAMPLE_HISTORIES[project.id % SAMPLE_HISTORIES.length];

  return (
    <Link
      to={`/projects/${project.id}`}
      className="flex h-full flex-col overflow-clip rounded-xl border bg-card text-foreground no-underline active:bg-accent pointer-fine:hover:bg-accent motion-safe:transition-[background-color,scale] motion-safe:duration-150 motion-safe:active:scale-[0.98]"
    >
      <div className="mx-2 mt-2 grid h-40 place-items-center rounded-[8px] bg-background">
        <NetworkIcon
          aria-hidden="true"
          strokeWidth={1.4}
          className="size-14 rotate-180 text-connection"
        />
      </div>
      <div className="flex flex-1 flex-col gap-2 px-4 pt-4 pb-2">
        <h2 className="font-display text-[26px] leading-[1.1] font-[660] tracking-[-0.025em] [overflow-wrap:anywhere]">
          {project.name}
        </h2>
        {project.description && (
          <p className="line-clamp-2 type-body text-muted-foreground">
            {project.description}
          </p>
        )}
        <div className="mt-auto flex flex-col gap-2 pt-3">
          {history ? (
            <span className="type-caption font-medium text-muted-foreground">
              Example runs
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 type-caption font-medium text-muted-foreground">
              <PauseIcon aria-hidden="true" className="size-3.5" />
              Not run yet
            </span>
          )}
          <div aria-hidden="true" className="grid grid-cols-16 gap-0.5">
            {Array.from({ length: HISTORY_LENGTH }, (_, index) => (
              <i
                key={index}
                className={`h-[18px] rounded-[2px] ${
                  history
                    ? RUN_CLASS[history[index]]
                    : "border border-dashed border-input"
                }`}
              />
            ))}
          </div>
        </div>
      </div>
      <div className="flex flex-wrap justify-between gap-x-3 gap-y-1.5 px-4 pt-3 pb-4 type-caption text-muted-foreground">
        <span>Updated {formatUpdated(project.updatedAt)}</span>
        <span className="inline-flex items-center gap-1.5">
          <LockIcon aria-hidden="true" className="size-3.5" />
          Private
        </span>
      </div>
    </Link>
  );
}
