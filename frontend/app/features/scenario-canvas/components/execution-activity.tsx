import { ActivityIcon, XIcon } from "lucide-react";
import { Button } from "~/components/ui/button";
import { nodeView, toneText } from "../execution-status";
import type { ExecutionDTO } from "../model/ExecutionDTO";
import type { ExecutionEventDTO } from "../model/ExecutionEventDTO";
import type { StreamState } from "../use-execution-events";

const PHASES: Record<NonNullable<ExecutionEventDTO["phase"]>, string> = {
  RESERVED: "Run reserved the scenario",
  STARTING: "Starting the components",
  RUNNING: "Every component is healthy",
  STOPPING: "Removing the containers",
  RELEASED: "Cleanup finished",
};

const CAUSES: Record<NonNullable<ExecutionEventDTO["cause"]>, string> = {
  STOP_REQUESTED: "Stop requested",
  TIME_LIMIT: "Time limit reached",
  FAILURE: "Failed",
  INTERRUPTION: "Interrupted by a backend restart",
};

const STREAM: Record<StreamState, string | null> = {
  connecting: "Connecting…",
  open: "Live",
  reconnecting: "Reconnecting…",
  failed: "Not receiving updates. Reload the page to try again.",
  closed: null,
};

// What one event says, in words.
export function describe(event: ExecutionEventDTO): string {
  if (event.kind === "LOG") return event.line ?? "";
  if (event.kind === "STATE") return event.status ? nodeView(event.status).label : "";
  switch (event.type) {
    case "PHASE":
      return event.phase ? PHASES[event.phase] : "";
    case "STOP": {
      const cause = event.cause ? CAUSES[event.cause] : "Stopping";
      return event.reason ? `${cause}: ${event.reason}` : cause;
    }
    case "CLEANUP_FAILED":
      return `Cleanup failed (attempt ${event.attempt}): ${event.error}`;
    case "RESPONSE":
      return `Answered ${event.response}`;
    case "LOGS_OMITTED":
      return `${event.count} more log lines left out`;
    default:
      return "";
  }
}

// Time of day by the browser's clock, to the second.
function time(at: string) {
  return new Date(at).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

// What happened during the experiment, newest last: states, lifecycle steps and what the components and
// the load printed, cleaned of secrets.
export default function ExecutionActivity({
  execution,
  events,
  stream,
  onClose,
}: Readonly<{
  execution: ExecutionDTO;
  events: ExecutionEventDTO[];
  stream: StreamState;
  onClose: () => void;
}>) {
  const names = new Map(
    execution.components.map((component) => [component.key, component.name]),
  );
  const status = STREAM[stream];

  function source(event: ExecutionEventDTO) {
    if (event.componentKey) return names.get(event.componentKey) ?? "Component";
    return event.kind === "LOG" ? "Load" : "Experiment";
  }

  return (
    <section aria-labelledby="activity-heading" className="flex flex-col gap-5">
      <div className="flex min-w-0 items-center gap-3">
        <span
          aria-hidden
          className="grid size-8 shrink-0 place-items-center rounded-lg bg-accent"
        >
          <ActivityIcon className="size-4" strokeWidth={1.6} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="type-caption text-muted-foreground">
            Revision {execution.scenarioRevision}
          </p>
          <h2
            id="activity-heading"
            className="font-display text-[15px] leading-5 font-semibold"
          >
            Activity
          </h2>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Close activity"
          onClick={onClose}
          className="shrink-0 self-start text-muted-foreground"
        >
          <XIcon />
        </Button>
      </div>
      {status && (
        <p
          role="status"
          className={`type-small ${stream === "failed" ? "text-destructive" : "text-muted-foreground"}`}
        >
          {status}
        </p>
      )}
      {events.length === 0 ? (
        <p className="type-small text-muted-foreground">
          {stream === "failed"
            ? "No events arrived."
            : "Events appear here as the experiment starts."}
        </p>
      ) : (
        <ol aria-label="Events" className="flex flex-col gap-3">
          {events.map((event) => (
            <li key={event.id} className="flex min-w-0 flex-col gap-0.5">
              <span className="flex gap-2 type-caption text-muted-foreground">
                <time dateTime={event.at} className="font-mono tabular-nums">
                  {time(event.at)}
                </time>
                <span className="truncate">{source(event)}</span>
              </span>
              <span
                className={
                  event.kind === "LOG"
                    ? "font-mono text-xs break-words whitespace-pre-wrap"
                    : `type-small break-words ${
                        event.kind === "STATE" && event.status
                          ? toneText[nodeView(event.status).tone]
                          : ""
                      }`
                }
              >
                {describe(event)}
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
