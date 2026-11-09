import { GaugeIcon } from "lucide-react";
import type { CSSProperties } from "react";
import { cn } from "cn";

// What the target node draws of the load: the rate and whether k6 is sending it right now.
export interface Traffic {
  label: string;
  // Requests per second, or null when the profile has no rate.
  rate: number | null;
  sending: boolean;
}

// Length of the lane the requests travel, in pixels.
const LANE = 150;
const PACKETS = [0, 1, 2, 3];

// Faster traffic moves faster: from 2.4 s per trip at 1 req/s down to 0.5 s.
function tripSeconds(rate: number | null) {
  if (!rate) return 2.4;
  return Math.min(2.4, Math.max(0.5, 2.4 / Math.sqrt(rate)));
}

// The load entering its target from the left. It is not an arrow of the graph (ADR 0012): it cannot be
// selected, connected or removed here, only in the Load panel. Requests only move while k6 sends them,
// because DESIGN.md keeps motion for changes caused by the user or by the execution.
export function TrafficLane({ traffic }: Readonly<{ traffic: Traffic }>) {
  const trip = tripSeconds(traffic.rate);
  return (
    <div
      aria-hidden
      data-testid="traffic"
      data-sending={traffic.sending || undefined}
      className={cn(
        "enter-soft pointer-events-none absolute top-1.5 right-full mr-1 flex flex-col items-end gap-1.5",
        traffic.sending
          ? "traffic-sending text-healthy-text"
          : "text-connection",
      )}
      style={
        {
          "--traffic-run": `${LANE - 14}px`,
          "--traffic-trip": `${trip}s`,
        } as CSSProperties
      }
    >
      <span
        className={cn(
          "flex items-center gap-1.5 rounded-full border bg-card px-2 py-1 font-mono text-xs leading-4 font-medium tabular-nums shadow-xs motion-safe:transition-colors motion-safe:duration-400",
          traffic.sending
            ? "border-healthy-text/50 text-healthy-text"
            : "border-input text-muted-foreground",
        )}
      >
        <GaugeIcon className="size-3.5" strokeWidth={1.8} />
        {traffic.label}
        {traffic.sending && (
          <span className="traffic-live size-2 rounded-full bg-healthy-text" />
        )}
      </span>
      <span className="relative block h-3.5" style={{ width: LANE }}>
        {/* The lane: dashed at rest, solid while requests flow. */}
        <span
          className={cn(
            "absolute top-1/2 right-2 left-0 -translate-y-1/2 border-t-2 border-current motion-safe:transition-opacity motion-safe:duration-400",
            traffic.sending ? "opacity-35" : "border-dashed opacity-80",
          )}
        />
        {PACKETS.map((packet) => (
          <span
            key={packet}
            className="traffic-packet absolute top-1/2 left-0 size-2 rounded-full bg-current"
            style={
              {
                "--traffic-at": `${((packet + 0.5) / PACKETS.length) * (LANE - 14)}px`,
                // Negative delays spread the requests along the lane from the first frame.
                animationDelay: `${-(trip * packet) / PACKETS.length}s`,
              } as CSSProperties
            }
          />
        ))}
        <svg
          className="absolute top-0 right-0"
          width="12"
          height="14"
          viewBox="0 0 12 14"
        >
          <path d="M1 1 L11 7 L1 13 Z" fill="currentColor" />
        </svg>
      </span>
    </div>
  );
}
