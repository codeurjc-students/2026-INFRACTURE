import type { Node, NodeProps } from "@xyflow/react";
import { TriangleAlertIcon } from "lucide-react";
import { memo } from "react";
import { cn } from "cn";
import type { ComponentNodeData } from "../graph-reducer";
import ComponentIcon from "./component-icon";

// A node ready to draw: its data plus the template name and its issues, added by the editor.
export type DisplayedNode = Node<
  ComponentNodeData & {
    templateName: string;
    issueCount: number;
    issueFields: string[];
  },
  "component"
>;

// The node reads like an instrument: what it is, then its configuration in mono, as DESIGN.md asks for technical data.
function ComponentNodeView({ data, selected }: NodeProps<DisplayedNode>) {
  const parameters = Object.entries(data.parameters);

  return (
    <div
      className={cn(
        "enter-soft flex w-56 flex-col gap-3 rounded-xl border border-input bg-card p-3 text-card-foreground",
        selected && "outline-2 outline-offset-3 outline-foreground",
      )}
    >
      {/* Header: type icon, template name and the node's own name. */}
      <div className="flex min-w-0 items-center gap-2.5">
        <ComponentIcon type={data.type} className="size-7.5" />
        <div className="min-w-0">
          <p className="truncate type-caption text-muted-foreground">
            {data.templateName}
          </p>
          <p
            title={data.name}
            className="truncate text-sm leading-5 font-[580]"
          >
            {data.name.trim() || "Unnamed"}
          </p>
        </div>
      </div>
      {/* Configuration: profile and each parameter. A value with an issue turns amber. */}
      <dl className="flex flex-col gap-1 rounded-lg bg-background px-2.5 py-2 font-mono text-xs leading-4">
        {[["profile", data.profile ?? "—"] as const, ...parameters].map(
          ([name, value]) => (
            <div key={name} className="flex min-w-0 justify-between gap-3">
              <dt className="truncate text-muted-foreground">{name}</dt>
              <dd
                className={cn(
                  "shrink-0 tabular-nums",
                  data.issueFields.includes(name) && "text-degraded-text",
                )}
              >
                {value}
              </dd>
            </div>
          ),
        )}
      </dl>
      {data.issueCount > 0 && (
        <p className="flex items-center gap-1.5 type-caption font-[520] text-degraded-text">
          <TriangleAlertIcon className="size-3.5" aria-hidden />
          {data.issueCount === 1
            ? "1 issue to review"
            : `${data.issueCount} issues to review`}
        </p>
      )}
    </div>
  );
}

// memo: React Flow redraws a node only when its own props change.
export default memo(ComponentNodeView);
