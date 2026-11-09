import { ListChecksIcon, XIcon } from "lucide-react";
import { Button } from "~/components/ui/button";
import type { ValidationIssueDTO } from "../model/ValidationIssueDTO";
import IssueNote from "./issue-note";

// An issue ready to list: where it is, in words, and the node that shows it (null for the scenario),
// or whether it belongs to the load, which has its own panel.
export interface LocatedIssue {
  issue: ValidationIssueDTO;
  where: string;
  nodeKey: string | null;
  load?: boolean;
}

// Errors first, then warnings, keeping the backend order inside each group.
function bySeverity(a: LocatedIssue, b: LocatedIssue) {
  return (
    Number(a.issue.severity !== "ERROR") - Number(b.issue.severity !== "ERROR")
  );
}

// The scenario's issues from the last save, in the side panel. Picking one selects its node or opens
// the load.
export default function IssueList({
  issues,
  stale,
  onLocate,
  onLocateLoad,
  onClose,
}: Readonly<{
  issues: LocatedIssue[];
  // There are edits after the save these issues come from.
  stale: boolean;
  onLocate: (nodeKey: string) => void;
  onLocateLoad: () => void;
  onClose: () => void;
}>) {
  const errors = issues.filter(({ issue }) => issue.severity === "ERROR");
  let summary = "Warnings do not block running the scenario.";
  if (issues.length === 0) {
    summary = "The last save has no issues.";
  } else if (errors.length === 1) {
    summary = "1 error blocks running the scenario. Warnings do not.";
  } else if (errors.length > 1) {
    summary = `${errors.length} errors block running the scenario. Warnings do not.`;
  }

  return (
    <section aria-labelledby="issues-heading" className="flex flex-col gap-5">
      <div className="flex min-w-0 items-center gap-3">
        <span
          aria-hidden
          className="grid size-8 shrink-0 place-items-center rounded-lg bg-accent"
        >
          <ListChecksIcon className="size-4" strokeWidth={1.6} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="type-caption text-muted-foreground">Scenario</p>
          <h2
            id="issues-heading"
            className="font-display text-[15px] leading-5 font-semibold"
          >
            Issues
          </h2>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Close issues"
          onClick={onClose}
          className="shrink-0 self-start text-muted-foreground"
        >
          <XIcon />
        </Button>
      </div>
      <p className="type-small text-muted-foreground">{summary}</p>
      {stale && (
        <p className="rounded-lg bg-accent px-3 py-2 type-small">
          These issues come from the last save. Save to check your changes.
        </p>
      )}
      <ul className="-mx-2 flex flex-col">
        {[...issues].sort(bySeverity).map(({ issue, where, nodeKey, load }) => {
          const content = (
            <>
              <IssueNote severity={issue.severity} message={issue.message} />
              <span className="pl-5 type-caption text-muted-foreground">
                {where}
              </span>
            </>
          );
          return (
            <li key={`${issue.code}-${issue.target.key}-${issue.field}`}>
              {nodeKey || load ? (
                <button
                  type="button"
                  onClick={() => (nodeKey ? onLocate(nodeKey) : onLocateLoad())}
                  className="flex w-full flex-col gap-0.5 rounded-md px-2 py-2 text-left active:bg-accent pointer-fine:hover:bg-accent motion-safe:transition-colors"
                >
                  {content}
                </button>
              ) : (
                <div className="flex flex-col gap-0.5 px-2 py-2">{content}</div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
