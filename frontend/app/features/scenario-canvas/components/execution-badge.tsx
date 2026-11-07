import { cn } from "cn";
import { executionView, toneText } from "../execution-status";
import type { ExecutionDTO } from "../model/ExecutionDTO";
import { StatusIcon } from "./status-icon";

// The experiment's state in the top bar. It is a live region, so screen readers hear each change.
export function ExecutionBadge({
  execution,
}: Readonly<{ execution: ExecutionDTO }>) {
  const view = executionView(execution);
  return (
    <output
      aria-live="polite"
      title={
        execution.failureReason ?? `Revision ${execution.scenarioRevision}`
      }
      className={cn(
        "flex shrink-0 items-center gap-1.5 text-sm font-[520] max-sm:sr-only",
        toneText[view.tone],
      )}
    >
      <StatusIcon tone={view.tone} />
      {view.label}
    </output>
  );
}
