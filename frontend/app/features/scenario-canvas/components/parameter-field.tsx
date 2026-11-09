import { useId, useState } from "react";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "~/components/ui/field";
import { Input } from "~/components/ui/input";
import type { ValidationIssueDTO } from "../model/ValidationIssueDTO";
import IssueNote from "./issue-note";

// Parameters are integers: an optional minus sign and digits, nothing else.
const WHOLE_NUMBER = /^-?\d+$/;

// One numeric parameter of a contract, for a node or for the load. It keeps what the user types apart
// from the graph value, so the field does not jump while typing, and only ever sends whole numbers
// inside the range.
export default function ParameterField({
  parameter,
  value,
  issue,
  onChange,
}: Readonly<{
  parameter: { name: string; min: number; max: number };
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
