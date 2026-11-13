import { cn } from "cn";
import { useEffect, useState } from "react";
import { executionView, timeLeftLabel, toneText } from "../execution-status";
import type { ExecutionDTO } from "../model/ExecutionDTO";
import { StatusIcon } from "./status-icon";

// The experiment's state in the top bar. It is a live region, so screen readers hear each change.
export function ExecutionBadge({
  execution,
}: Readonly<{ execution: ExecutionDTO }>) {
  const view = executionView(execution);
  return (
    <>
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
      {execution.secondsLeft !== null && (
        <TimeLeft key={execution.secondsLeft} seconds={execution.secondsLeft} />
      )}
    </>
  );
}

// Counts down between polls from the server's figure. Each poll brings a new figure and remounts it,
// so the server's clock always decides; the server, not this timer, ends the experiment.
function TimeLeft({ seconds }: Readonly<{ seconds: number }>) {
  const [left, setLeft] = useState(seconds);

  useEffect(() => {
    const timer = setInterval(
      () => setLeft((current) => Math.max(0, current - 1)),
      1000,
    );
    return () => clearInterval(timer);
  }, []);

  return (
    <span
      role="timer"
      aria-label="Time left"
      className="shrink-0 font-mono text-sm text-muted-foreground tabular-nums max-sm:sr-only"
    >
      {timeLeftLabel(left)}
    </span>
  );
}
