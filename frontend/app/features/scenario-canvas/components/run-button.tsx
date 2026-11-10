import { PlayIcon, RotateCcwIcon, SquareIcon } from "lucide-react";
import { Button } from "~/components/ui/button";
import { Spinner } from "~/components/ui/spinner";

interface RunButtonProps {
  // An execution holds the scenario, so the button stops it instead of running it.
  active: boolean;
  stopping: boolean;
  // The cleanup failed and waits for the owner, so the button retries it.
  retryCleanup: boolean;
  pending: boolean;
  // Why Run cannot start now, or null when it can.
  blockedBy: string | null;
  onRun: () => void;
  onStop: () => void;
}

// Run when the scenario is free, Stop while an experiment holds it.
export function RunButton({
  active,
  stopping,
  retryCleanup,
  pending,
  blockedBy,
  onRun,
  onStop,
}: Readonly<RunButtonProps>) {
  if (active && retryCleanup) {
    return (
      <Button
        type="button"
        variant="outline"
        onClick={onStop}
        disabled={pending}
        title="Try again to remove the experiment's containers, network and volumes"
        className="min-w-20 px-3"
      >
        {pending ? (
          <Spinner data-icon="inline-start" aria-hidden="true" />
        ) : (
          <RotateCcwIcon data-icon="inline-start" />
        )}
        Retry cleanup
      </Button>
    );
  }
  if (active) {
    return (
      <Button
        type="button"
        variant="outline"
        onClick={onStop}
        disabled={pending || stopping}
        title={
          stopping
            ? "The experiment is stopping"
            : "Stop the experiment and remove its containers"
        }
        className="min-w-20 px-3"
      >
        {pending || stopping ? (
          <Spinner data-icon="inline-start" aria-hidden="true" />
        ) : (
          <SquareIcon data-icon="inline-start" />
        )}
        {stopping ? "Stopping…" : "Stop"}
      </Button>
    );
  }
  return (
    <Button
      type="button"
      variant="outline"
      onClick={onRun}
      disabled={pending || blockedBy !== null}
      title={blockedBy ?? "Run the saved scenario"}
      className="min-w-20 px-3"
    >
      {pending ? (
        <Spinner data-icon="inline-start" aria-hidden="true" />
      ) : (
        <PlayIcon data-icon="inline-start" />
      )}
      Run
    </Button>
  );
}
