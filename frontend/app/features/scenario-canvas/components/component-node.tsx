import {
  Handle,
  Position,
  useConnection,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import { CircleXIcon, TriangleAlertIcon } from "lucide-react";
import { memo } from "react";
import { cn } from "cn";
import type { ComponentNodeData } from "../graph-reducer";
import ComponentIcon from "./component-icon";

// A node ready to draw: its data plus the template name and its issues, added by the editor.
export type DisplayedNode = Node<
  ComponentNodeData & {
    templateName: string;
    errorCount: number;
    warningCount: number;
    // Fields with a contract error.
    issueFields: string[];
    // The outgoing ports drawn on the node. "stale" ports are not in the profile any more but still
    // have an arrow, so the arrow stays visible until the user removes it.
    ports: { name: string; label: string; stale: boolean }[];
  },
  "component"
>;

// "1 error", "2 warnings", or nothing for zero.
function count(value: number, noun: string) {
  if (value === 0) return "";
  return value === 1 ? `1 ${noun}` : `${value} ${noun}s`;
}

// The node reads like an instrument: what it is, then its configuration in mono, as DESIGN.md asks for technical data.
function ComponentNodeView({ data, selected }: NodeProps<DisplayedNode>) {
  const parameters = Object.entries(data.parameters);
  // While an arrow is being drawn, the whole node is the drop target, not only its small handle.
  const connecting = useConnection((connection) => connection.inProgress);

  return (
    <div
      className={cn(
        "enter-soft flex w-56 flex-col gap-3 rounded-xl border border-input bg-card p-3 text-card-foreground",
        selected && "outline-2 outline-offset-3 outline-foreground",
      )}
    >
      <Handle
        type="target"
        position={Position.Left}
        isConnectableStart={false}
        className={cn(
          "size-3! border-input! bg-card!",
          connecting &&
            "top-0! left-0! size-full! transform-none! rounded-xl! opacity-0!",
        )}
      />
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
      {/* Configuration: profile and each parameter. A value with an error turns red. */}
      <dl className="flex flex-col gap-1 rounded-lg bg-background px-2.5 py-2 font-mono text-xs leading-4">
        {[["profile", data.profile ?? "—"] as const, ...parameters].map(
          ([name, value]) => (
            <div key={name} className="flex min-w-0 justify-between gap-3">
              <dt className="truncate text-muted-foreground">{name}</dt>
              <dd
                className={cn(
                  "shrink-0 tabular-nums",
                  data.issueFields.includes(name) && "text-destructive",
                )}
              >
                {value}
              </dd>
            </div>
          ),
        )}
      </dl>
      {/* Outgoing ports: drag from a dot, or tap it and then the target, to draw an arrow. */}
      {data.ports.length > 0 && (
        <ul className="-mr-3 flex flex-col gap-1.5">
          {data.ports.map((port) => (
            <li
              key={port.name}
              className={cn(
                "relative pr-4 text-right type-caption text-muted-foreground",
                port.stale && "text-degraded-text line-through",
              )}
            >
              {port.label}
              <Handle
                type="source"
                id={port.name}
                position={Position.Right}
                isConnectable={!port.stale}
                className="size-3! border-foreground! bg-card!"
              />
            </li>
          ))}
        </ul>
      )}
      {data.errorCount + data.warningCount > 0 && (
        <p
          className={cn(
            "flex items-center gap-1.5 type-caption font-[520]",
            data.errorCount > 0 ? "text-destructive" : "text-degraded-text",
          )}
        >
          {data.errorCount > 0 ? (
            <CircleXIcon className="size-3.5" aria-hidden />
          ) : (
            <TriangleAlertIcon className="size-3.5" aria-hidden />
          )}
          {[
            count(data.errorCount, "error"),
            count(data.warningCount, "warning"),
          ]
            .filter(Boolean)
            .join(", ")}
        </p>
      )}
    </div>
  );
}

// memo: React Flow redraws a node only when its own props change.
export default memo(ComponentNodeView);
