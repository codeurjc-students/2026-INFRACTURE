import { Trash2Icon, TriangleAlertIcon, XIcon } from "lucide-react";
import { useId, useState, type Dispatch } from "react";
import { Button } from "~/components/ui/button";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "~/components/ui/field";
import { Input } from "~/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "~/components/ui/select";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import type { TemplateContractDTO } from "~/features/component-catalogue/model/TemplateContractDTO";
import type { CanvasAction, ComponentNode } from "../graph-reducer";
import type { ValidationIssueDTO } from "../model/ValidationIssueDTO";
import ComponentIcon from "./component-icon";

// Same limit as the backend.
const NAME_MAX_LENGTH = 30;
// Parameters are integers: an optional minus sign and digits, nothing else.
const WHOLE_NUMBER = /^-?\d+$/;

// A contract issue lets the draft be saved, so it reads as a warning, not as an input error.
function IssueNote({ id, message }: { id?: string; message: string }) {
  return (
    <p id={id} className="flex gap-1.5 type-small text-degraded-text">
      <TriangleAlertIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
      {message}
    </p>
  );
}

// One numeric parameter of the contract. It keeps what the user types apart from the graph value,
// so the field does not jump while typing, and only ever sends whole numbers inside the range.
function ParameterField({
  parameter,
  value,
  issue,
  onChange,
}: {
  parameter: TemplateContractDTO["profiles"][number]["parameters"][number];
  value: number | undefined;
  issue: string | undefined;
  onChange: (value: number | undefined) => void;
}) {
  const id = useId();
  const [text, setText] = useState(value === undefined ? "" : String(value));
  const clamp = (number: number) =>
    Math.min(Math.max(number, parameter.min), parameter.max);
  const whole = WHOLE_NUMBER.test(text);
  const inputError =
    text !== "" && !whole
      ? "Use a whole number."
      : whole && clamp(Number(text)) !== Number(text)
        ? `Use a value from ${parameter.min} to ${parameter.max}.`
        : undefined;

  return (
    <Field data-invalid={Boolean(inputError) || undefined}>
      <FieldLabel htmlFor={id} className="font-mono">
        {parameter.name}
      </FieldLabel>
      <Input
        id={id}
        type="number"
        inputMode="numeric"
        step={1}
        min={parameter.min}
        max={parameter.max}
        value={text}
        className="font-mono tabular-nums"
        aria-invalid={Boolean(inputError) || undefined}
        aria-describedby={`${id}-help${issue ? ` ${id}-issue` : ""}`}
        onChange={(event) => {
          const next = event.target.value;
          setText(next);
          // Only whole numbers inside the contract reach the graph; the field settles on blur.
          if (next === "") onChange(undefined);
          else if (WHOLE_NUMBER.test(next)) onChange(clamp(Number(next)));
        }}
        // On leaving the field it settles on a valid value: the minimum if empty, the range limit if
        // outside it, or the last valid value if it was not a whole number.
        onBlur={() => {
          const settled =
            text === ""
              ? parameter.min
              : whole
                ? clamp(Number(text))
                : (value ?? parameter.min);
          setText(String(settled));
          onChange(settled);
        }}
      />
      {inputError ? (
        <FieldError id={`${id}-help`}>{inputError}</FieldError>
      ) : (
        <FieldDescription id={`${id}-help`}>
          {`Whole number from ${parameter.min} to ${parameter.max}.`}
        </FieldDescription>
      )}
      {issue && !inputError && <IssueNote id={`${id}-issue`} message={issue} />}
    </Field>
  );
}

// The selected node's form. Every field comes from the template's contract,
// and every edit is sent to the reducer through dispatch.
export default function NodeInspector({
  node,
  template,
  issues,
  dispatch,
  onClose,
}: {
  node: ComponentNode;
  template: ComponentTemplateDTO | undefined;
  issues: ValidationIssueDTO[];
  dispatch: Dispatch<CanvasAction>;
  onClose: () => void;
}) {
  const nameId = useId();
  const profileId = useId();
  const { data } = node;
  const profiles = template?.contract.profiles ?? [];
  const profile = profiles.find((candidate) => candidate.name === data.profile);
  // The issue of one field, shown under that field.
  const fieldIssue = (field: string) =>
    issues.find((issue) => issue.field === field)?.message;
  // Issues that belong to no visible field, shown at the end.
  const otherIssues = issues.filter(
    (issue) =>
      issue.field !== "profile" &&
      !profile?.parameters.some((parameter) => parameter.name === issue.field),
  );
  const nameError = data.name.trim() ? undefined : "Enter a name.";
  const profileIssue = fieldIssue("profile");

  return (
    <section
      aria-labelledby="inspector-heading"
      className="flex flex-col gap-5"
    >
      <div className="flex min-w-0 items-center gap-3">
        <ComponentIcon type={data.type} />
        <div className="min-w-0 flex-1">
          <p className="truncate type-caption text-muted-foreground">
            {template?.name ?? "Unavailable template"}
          </p>
          <h2
            id="inspector-heading"
            className="line-clamp-2 font-display text-[15px] leading-5 font-semibold [overflow-wrap:anywhere]"
          >
            {data.name.trim() || "Unnamed component"}
          </h2>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Close configuration"
          onClick={onClose}
          className="shrink-0 self-start text-muted-foreground"
        >
          <XIcon />
        </Button>
      </div>

      <div className="flex flex-col gap-4">
        <Field data-invalid={Boolean(nameError) || undefined}>
          <FieldLabel htmlFor={nameId}>Name</FieldLabel>
          <Input
            id={nameId}
            value={data.name}
            maxLength={NAME_MAX_LENGTH}
            aria-invalid={Boolean(nameError) || undefined}
            onChange={(event) =>
              dispatch({
                type: "renamed",
                key: node.id,
                name: event.target.value,
              })
            }
          />
          {nameError && <FieldError>{nameError}</FieldError>}
        </Field>

        {profiles.length > 0 && (
          <Field>
            <FieldLabel htmlFor={profileId}>Profile</FieldLabel>
            <Select
              value={data.profile ?? null}
              onValueChange={(value) => {
                const next = profiles.find(
                  (candidate) => candidate.name === value,
                );
                if (next)
                  dispatch({
                    type: "profileChanged",
                    key: node.id,
                    profile: next,
                  });
              }}
            >
              <SelectTrigger id={profileId} className="w-full font-mono">
                <SelectValue placeholder="Choose a profile" />
              </SelectTrigger>
              <SelectContent alignItemWithTrigger={false} align="start">
                {profiles.map((candidate) => (
                  <SelectItem
                    key={candidate.name}
                    value={candidate.name}
                    className="font-mono"
                  >
                    {candidate.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {profileIssue && <IssueNote message={profileIssue} />}
          </Field>
        )}

        {profile?.parameters.map((parameter) => (
          <ParameterField
            key={`${node.id}-${data.profile}-${parameter.name}`}
            parameter={parameter}
            value={data.parameters[parameter.name]}
            issue={fieldIssue(parameter.name)}
            onChange={(value) =>
              dispatch({
                type: "parameterChanged",
                key: node.id,
                name: parameter.name,
                value,
              })
            }
          />
        ))}
        {profile && profile.parameters.length === 0 && (
          <p className="type-small text-muted-foreground">
            {`The ${profile.name} profile has no parameters to set.`}
          </p>
        )}
      </div>

      {otherIssues.length > 0 && (
        <div className="flex flex-col gap-2">
          {otherIssues.map((issue) => (
            <IssueNote
              key={`${issue.code}-${issue.field}`}
              message={issue.message}
            />
          ))}
        </div>
      )}

      <Button
        type="button"
        variant="outline"
        onClick={() => dispatch({ type: "removed", key: node.id })}
        className="self-start"
      >
        <Trash2Icon data-icon="inline-start" />
        Delete component
      </Button>
    </section>
  );
}
