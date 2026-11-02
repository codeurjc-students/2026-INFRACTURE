import { Trash2Icon, UnplugIcon, XIcon } from "lucide-react";
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
import IssueNote from "./issue-note";

// Same limit as the backend.
const NAME_MAX_LENGTH = 30;
// Parameters are integers: an optional minus sign and digits, nothing else.
const WHOLE_NUMBER = /^-?\d+$/;

// One arrow of the selected node, ready to list: which way it goes, its port label and the other node.
export interface NodeConnection {
  id: string;
  outgoing: boolean;
  label: string;
  other: string;
  // Why it is an error (the contract no longer allows it, or it closes a cycle), or null.
  issue: string | null;
}

// One numeric parameter of the contract. It keeps what the user types apart from the graph value,
// so the field does not jump while typing, and only ever sends whole numbers inside the range.
function ParameterField({
  parameter,
  value,
  issue,
  onChange,
}: Readonly<{
  parameter: TemplateContractDTO["profiles"][number]["parameters"][number];
  value: number | undefined;
  issue: ValidationIssueDTO | undefined;
  onChange: (value: number | undefined) => void;
}>) {
  const id = useId();
  const [text, setText] = useState(value === undefined ? "" : String(value));
  const clamp = (number: number) =>
    Math.min(Math.max(number, parameter.min), parameter.max);
  const whole = WHOLE_NUMBER.test(text);
  let inputError: string | undefined;
  if (text !== "" && !whole) {
    inputError = "Use a whole number.";
  } else if (whole && clamp(Number(text)) !== Number(text)) {
    inputError = `Use a value from ${parameter.min} to ${parameter.max}.`;
  }
  // The help or error text, plus the contract issue when there is one.
  const describedBy = issue ? `${id}-help ${id}-issue` : `${id}-help`;

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
        aria-describedby={describedBy}
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
          let settled = value ?? parameter.min;
          if (text === "") settled = parameter.min;
          else if (whole) settled = clamp(Number(text));
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
      {issue && !inputError && (
        <IssueNote
          id={`${id}-issue`}
          severity={issue.severity}
          message={issue.message}
        />
      )}
    </Field>
  );
}

// The selected node's form. Every field comes from the template's contract,
// and every edit is sent to the reducer through dispatch.
export default function NodeInspector({
  node,
  template,
  issues,
  connections,
  dispatch,
  onClose,
}: Readonly<{
  node: ComponentNode;
  template: ComponentTemplateDTO | undefined;
  issues: ValidationIssueDTO[];
  connections: NodeConnection[];
  dispatch: Dispatch<CanvasAction>;
  onClose: () => void;
}>) {
  const nameId = useId();
  const profileId = useId();
  const { data } = node;
  const profiles = template?.contract.profiles ?? [];
  const profile = profiles.find((candidate) => candidate.name === data.profile);
  // The issue of one field, shown under that field.
  const fieldIssue = (field: string) =>
    issues.find((issue) => issue.field === field);
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
            {profileIssue && (
              <IssueNote
                severity={profileIssue.severity}
                message={profileIssue.message}
              />
            )}
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
        {profile?.parameters.length === 0 && (
          <p className="type-small text-muted-foreground">
            {`The ${profile.name} profile has no parameters to set.`}
          </p>
        )}
      </div>

      {/* Connections: the keyboard and touch way to see and remove arrows. */}
      {(connections.length > 0 || (profile?.ports.length ?? 0) > 0) && (
        <section
          aria-labelledby="connections-heading"
          className="flex flex-col gap-2"
        >
          <h3 id="connections-heading" className="text-sm font-medium">
            Connections
          </h3>
          {connections.length === 0 ? (
            <p className="type-small text-muted-foreground">
              Drag from a port on the node to a component that it can use.
            </p>
          ) : (
            <ul className="flex flex-col gap-1">
              {connections.map((connection) => {
                const text = connection.outgoing
                  ? `${connection.label} → ${connection.other}`
                  : `${connection.other} → ${connection.label}`;
                return (
                  <li key={connection.id} className="flex flex-col gap-1">
                    <span className="flex min-w-0 items-center justify-between gap-2">
                      <span className="truncate text-sm">{text}</span>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Remove connection ${text}`}
                        title="Remove connection"
                        onClick={() =>
                          dispatch({ type: "disconnected", id: connection.id })
                        }
                        className="shrink-0 text-muted-foreground"
                      >
                        <UnplugIcon />
                      </Button>
                    </span>
                    {connection.issue && (
                      <IssueNote severity="ERROR" message={connection.issue} />
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      )}

      {otherIssues.length > 0 && (
        <div className="flex flex-col gap-2">
          {otherIssues.map((issue) => (
            <IssueNote
              key={`${issue.code}-${issue.field}`}
              severity={issue.severity}
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
