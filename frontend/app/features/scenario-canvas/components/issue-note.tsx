import { CircleXIcon, TriangleAlertIcon } from "lucide-react";
import { cn } from "cn";
import type { ValidationIssueDTO } from "../model/ValidationIssueDTO";

// One validation issue in a line. Both kinds let the draft be saved; an error blocks running it.
// DESIGN.md: an error is a circled cross in failed-text, a warning a triangle in degraded-text.
export default function IssueNote({
  id,
  severity,
  message,
  className,
}: Readonly<{
  id?: string;
  severity: ValidationIssueDTO["severity"];
  message: string;
  className?: string;
}>) {
  const Icon = severity === "ERROR" ? CircleXIcon : TriangleAlertIcon;
  return (
    <p
      id={id}
      className={cn(
        "flex gap-1.5 type-small",
        severity === "ERROR" ? "text-destructive" : "text-degraded-text",
        className,
      )}
    >
      <Icon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
      <span>
        <span className="sr-only">
          {severity === "ERROR" ? "Error:" : "Warning:"}{" "}
        </span>
        {message}
      </span>
    </p>
  );
}
