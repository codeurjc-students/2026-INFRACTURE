import { CircleCheckIcon, InfoIcon, XIcon } from "lucide-react";
import { useState } from "react";
import { useFetcher } from "react-router";
import { Button } from "~/components/ui/button";
import DetailsForm from "~/features/projects/components/details-form";
import type { DetailsFormResult } from "~/features/projects/details-form-data";
import type { ScenarioDTO } from "~/features/projects/model/ScenarioDTO";

type SaveResult = DetailsFormResult | { saved: true };

// The scenario's name and description, edited in the same side panel as a component.
export default function ScenarioDetails({
  scenario,
  revision,
  onClose,
}: {
  scenario: ScenarioDTO;
  revision: number;
  onClose: () => void;
}) {
  const fetcher = useFetcher<SaveResult>();
  const [result, setResult] = useState(fetcher.data);
  // "Changes saved" disappears as soon as the user edits again.
  const [edited, setEdited] = useState(false);
  if (fetcher.data !== result) {
    setResult(fetcher.data);
    setEdited(false);
  }
  const saved = !edited && result !== undefined && "saved" in result;

  return (
    <section
      aria-labelledby="scenario-details-heading"
      className="flex flex-col gap-5"
    >
      <div className="flex min-w-0 items-center gap-3">
        <span
          aria-hidden
          className="grid size-8 shrink-0 place-items-center rounded-lg bg-accent"
        >
          <InfoIcon className="size-4" strokeWidth={1.6} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="type-caption text-muted-foreground">
            {`Scenario, revision ${revision}`}
          </p>
          <h2
            id="scenario-details-heading"
            className="font-display text-[15px] leading-5 font-semibold"
          >
            Details
          </h2>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Close details"
          onClick={onClose}
          className="shrink-0 self-start text-muted-foreground"
        >
          <XIcon />
        </Button>
      </div>
      {/* The key resets the fields to the saved values after each save. */}
      <fetcher.Form
        key={`${scenario.name}|${scenario.description}`}
        method="post"
        noValidate
        onChange={() => setEdited(true)}
      >
        <DetailsForm
          compact
          {...(result && !("saved" in result) ? result : {})}
          defaultName={scenario.name}
          defaultDescription={scenario.description ?? ""}
          namePlaceholder="Cold cache"
          errorTitle="Could not save the changes"
          submitLabel="Save changes"
          pendingLabel="Saving…"
          pending={fetcher.state !== "idle"}
          cancel={
            saved && (
              <p role="status" className="flex items-center gap-1.5 type-small">
                <CircleCheckIcon
                  className="size-4 text-healthy-text"
                  aria-hidden
                />
                Changes saved
              </p>
            )
          }
        />
      </fetcher.Form>
    </section>
  );
}
