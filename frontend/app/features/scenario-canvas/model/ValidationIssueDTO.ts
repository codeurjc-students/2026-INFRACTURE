// A contract issue: it does not block saving, and it says which element and field it is about.
export interface ValidationIssueDTO {
  severity: "ERROR" | "WARNING";
  code: string;
  message: string;
  target: {
    kind: "COMPONENT" | "CONNECTION" | "WORKLOAD" | "SCENARIO";
    key: string | null;
  };
  field: string | null;
}
