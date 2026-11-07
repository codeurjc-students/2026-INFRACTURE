import {
  CircleCheckIcon,
  CircleXIcon,
  PauseIcon,
  RefreshCwIcon,
  TriangleAlertIcon,
} from "lucide-react";
import { cn } from "cn";
import type { Tone } from "../execution-status";

// The icon DESIGN.md gives each state: check, arrow, cross, warning or pause.
export function StatusIcon({
  tone,
  className,
}: Readonly<{ tone: Tone; className?: string }>) {
  const props = {
    className: cn("size-3.5 shrink-0", className),
    "aria-hidden": true,
  };
  switch (tone) {
    case "healthy":
      return <CircleCheckIcon {...props} />;
    case "recovering":
      return <RefreshCwIcon {...props} />;
    case "failed":
      return <CircleXIcon {...props} />;
    case "degraded":
      return <TriangleAlertIcon {...props} />;
    case "paused":
      return <PauseIcon {...props} />;
  }
}
