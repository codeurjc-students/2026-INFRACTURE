import {
  BoxIcon,
  CpuIcon,
  DatabaseIcon,
  LayersIcon,
  Rows3Icon,
  ServerIcon,
  type LucideIcon,
} from "lucide-react";
import { cn } from "cn";

// Each component type is recognised by its icon and name, never by a colour (DESIGN.md).
const ICONS: Record<string, LucideIcon> = {
  HTTP_SERVICE: ServerIcon,
  WORKER: CpuIcon,
  POSTGRESQL: DatabaseIcon,
  REDIS: LayersIcon,
  RABBITMQ: Rows3Icon,
};

// The type icon in a neutral square. Unknown types get a generic box.
export default function ComponentIcon({
  type,
  className,
}: {
  type: string;
  className?: string;
}) {
  const Icon = ICONS[type] ?? BoxIcon;
  return (
    <span
      aria-hidden="true"
      className={cn(
        "grid size-8 shrink-0 place-items-center rounded-lg bg-accent text-foreground",
        className,
      )}
    >
      <Icon className="size-4" strokeWidth={1.6} />
    </span>
  );
}
