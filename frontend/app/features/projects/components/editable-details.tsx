import { CircleCheckIcon, PencilIcon } from "lucide-react";
import { useState, type ReactNode } from "react";
import { useFetcher } from "react-router";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import type { DetailsFormResult } from "../details-form-data";
import DetailsForm from "./details-form";

type SaveResult = DetailsFormResult | { saved: true };

// Page title, description and metadata, with in-place editing through the route action.
export default function EditableDetails({
  name,
  description,
  namePlaceholder,
  meta,
}: {
  name: string;
  description: string | null;
  namePlaceholder: string;
  meta: ReactNode;
}) {
  const fetcher = useFetcher<SaveResult>();
  const [editing, setEditing] = useState(false);
  const [draftName, setDraftName] = useState(name);
  const [result, setResult] = useState(fetcher.data);

  // Close the form once a save succeeds; the loader then brings the saved details.
  if (fetcher.data !== result) {
    setResult(fetcher.data);
    if (fetcher.data && "saved" in fetcher.data) {
      setEditing(false);
    }
  }

  const saved = !editing && result !== undefined && "saved" in result;
  const title = editing ? draftName.trim() || name : name;

  function startEditing() {
    setDraftName(name);
    setResult(undefined);
    setEditing(true);
  }

  return (
    <>
      {saved && (
        <Alert role="status" className="enter-soft max-w-3xl">
          <CircleCheckIcon className="text-healthy-text" />
          <AlertTitle>Changes saved</AlertTitle>
          <AlertDescription>
            The name and description are up to date.
          </AlertDescription>
        </Alert>
      )}
      <div className="flex flex-wrap items-end justify-between gap-x-10 gap-y-5">
        <div className="flex min-w-0 flex-[1_1_560px] flex-col gap-3.5">
          <h1 className="type-headline">{title}</h1>
          {!editing && description && (
            <p className="max-w-[52ch] type-title text-muted-foreground">
              {description}
            </p>
          )}
          <div className="flex flex-wrap gap-x-5 gap-y-1.5 type-small text-muted-foreground">
            {meta}
          </div>
        </div>
        {!editing && (
          <Button
            type="button"
            variant="outline"
            size="lg"
            onClick={startEditing}
            className="h-11 w-full px-4 sm:h-10 sm:w-auto"
          >
            <PencilIcon data-icon="inline-start" />
            Edit details
          </Button>
        )}
      </div>
      {editing && (
        <fetcher.Form method="post" noValidate className="max-w-3xl">
          <DetailsForm
            {...(result && !("saved" in result) ? result : {})}
            defaultName={name}
            defaultDescription={description ?? ""}
            namePlaceholder={namePlaceholder}
            errorTitle="Could not save the changes"
            submitLabel="Save changes"
            pendingLabel="Saving…"
            pending={fetcher.state !== "idle"}
            onNameChange={setDraftName}
            cancel={
              <Button
                type="button"
                variant="ghost"
                size="lg"
                onClick={() => setEditing(false)}
                className="px-4 text-muted-foreground"
              >
                Cancel
              </Button>
            }
          />
        </fetcher.Form>
      )}
    </>
  );
}
