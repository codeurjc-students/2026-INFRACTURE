import { CircleAlertIcon } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { Field, FieldLabel } from "~/components/ui/field";
import { Input } from "~/components/ui/input";
import { Spinner } from "~/components/ui/spinner";
import { Textarea } from "~/components/ui/textarea";
import {
  DESCRIPTION_MAX_LENGTH,
  NAME_MAX_LENGTH,
  type ProjectFormResult,
} from "../project-form-data";

type ProjectFormProps = ProjectFormResult & {
  defaultName?: string;
  defaultDescription?: string;
  errorTitle: string;
  submitLabel: string;
  pendingLabel: string;
  pending: boolean;
  cancel: ReactNode;
  onNameChange?: (name: string) => void;
};

// Counts characters as the backend does (code points), so emoji count as one.
function length(value: string) {
  return [...value].length;
}

// Fields and actions shared by the create and edit forms. The page provides the <Form>.
export default function ProjectForm({
  defaultName = "",
  defaultDescription = "",
  error,
  nameError,
  errorTitle,
  submitLabel,
  pendingLabel,
  pending,
  cancel,
  onNameChange,
}: ProjectFormProps) {
  const [nameLength, setNameLength] = useState(length(defaultName));
  const [descriptionLength, setDescriptionLength] = useState(
    length(defaultDescription),
  );

  return (
    <div className="flex flex-col gap-5 rounded-xl border bg-card p-5 sm:p-6">
      {error && (
        <Alert variant="destructive" className="enter-soft">
          <CircleAlertIcon />
          <AlertTitle>{errorTitle}</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <Field data-invalid={Boolean(nameError)}>
        <FieldLabel htmlFor="project-name" className="font-semibold">
          Name
        </FieldLabel>
        <Input
          id="project-name"
          name="name"
          defaultValue={defaultName}
          maxLength={NAME_MAX_LENGTH}
          autoComplete="off"
          autoFocus
          required
          placeholder="Cache lab"
          aria-invalid={Boolean(nameError)}
          aria-describedby="project-name-help"
          onChange={(event) => {
            setNameLength(length(event.target.value));
            onNameChange?.(event.target.value);
          }}
          className="h-14 px-4 text-xl font-[560] md:text-xl"
        />
        <div className="flex justify-between gap-4 type-small">
          <p
            id="project-name-help"
            className={nameError ? "text-destructive" : "text-muted-foreground"}
          >
            {nameError ?? `Up to ${NAME_MAX_LENGTH} characters.`}
          </p>
          <span
            aria-hidden="true"
            className="font-mono text-muted-foreground tabular-nums"
          >
            {`${nameLength}/${NAME_MAX_LENGTH}`}
          </span>
        </div>
      </Field>
      <Field>
        <FieldLabel htmlFor="project-description" className="font-semibold">
          Description
        </FieldLabel>
        <Textarea
          id="project-description"
          name="description"
          defaultValue={defaultDescription}
          maxLength={DESCRIPTION_MAX_LENGTH}
          rows={3}
          aria-describedby="project-description-help"
          onChange={(event) => setDescriptionLength(length(event.target.value))}
          className="min-h-28 px-4 py-3 text-[15px] leading-[22px] md:text-[15px]"
        />
        <div className="flex justify-between gap-4 type-small">
          <p id="project-description-help" className="text-muted-foreground">
            Optional. What do you want to learn from it?
          </p>
          <span
            aria-hidden="true"
            className="font-mono text-muted-foreground tabular-nums"
          >
            {`${descriptionLength}/${DESCRIPTION_MAX_LENGTH}`}
          </span>
        </div>
      </Field>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" size="lg" disabled={pending} className="px-4">
          {pending && <Spinner data-icon="inline-start" />}
          {pending ? pendingLabel : submitLabel}
        </Button>
        {cancel}
      </div>
    </div>
  );
}
