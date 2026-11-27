import { ChevronUpIcon } from "lucide-react";
import {
  useId,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type PointerEvent as ReactPointerEvent,
} from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { nodeView, toneText } from "../execution-status";
import type { ExecutionDTO } from "../model/ExecutionDTO";
import type {
  MetricComponentDTO,
  MetricSampleDTO,
} from "../model/ExecutionMetricsDTO";
import type { ExecutionMetricsSeries } from "../use-execution-metrics";
import { StatusIcon } from "./status-icon";

const BYTES_PER_MIB = 1024 * 1024;
const number = new Intl.NumberFormat("en", { maximumFractionDigits: 2 });

// The panel's height on wide screens: what the person dragged it to, kept between visits.
const HEIGHT_KEY = "infracture:metrics-panel:height";
const OPEN_KEY = "infracture:metrics-panel:open";
const DEFAULT_HEIGHT = 280;
const MIN_HEIGHT = 140;
const KEY_STEP = 24;

// Storage can be blocked or edited by hand: any problem means the default.
export function readPanelHeight() {
  try {
    const stored = Number(localStorage.getItem(HEIGHT_KEY));
    return Number.isFinite(stored) && stored >= MIN_HEIGHT
      ? stored
      : DEFAULT_HEIGHT;
  } catch {
    return DEFAULT_HEIGHT;
  }
}

export function readPanelOpen() {
  try {
    return localStorage.getItem(OPEN_KEY) !== "false";
  } catch {
    return true;
  }
}

function store(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Without storage the panel still works; it just opens at the default next time.
  }
}

export function writePanelOpen(open: boolean) {
  store(OPEN_KEY, String(open));
}

export interface ChartPoint {
  at: number;
  value: number | null;
}

// A null value stays null: the line breaks there instead of drawing a zero or joining the two sides.
export function cpuSeries(samples: MetricSampleDTO[]): ChartPoint[] {
  return samples.map((sample) => ({
    at: Date.parse(sample.at),
    value: sample.cpuPercent,
  }));
}

export function memorySeries(samples: MetricSampleDTO[]): ChartPoint[] {
  return samples.map((sample) => ({
    at: Date.parse(sample.at),
    value:
      sample.memoryBytes === null ? null : sample.memoryBytes / BYTES_PER_MIB,
  }));
}

// A gap says so in words, so it never reads as a zero.
export function reading(value: number | null, unit: string) {
  return value === null ? "No reading" : `${number.format(value)}${unit}`;
}

function clock(at: number) {
  return new Date(at).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

// A value with no neighbour draws no line, so it needs its own dot to be seen.
function isIsolated(points: ChartPoint[], index: number) {
  return (
    points[index]?.value != null &&
    points[index - 1]?.value == null &&
    points[index + 1]?.value == null
  );
}

function MetricChart({
  name,
  unit,
  points,
  limit,
  domain,
  showTime,
}: Readonly<{
  name: string;
  // Written right after the number: "%" or " MiB".
  unit: string;
  points: ChartPoint[];
  limit: number;
  // Every row shares the same time range, so a moment lines up across components.
  domain: [number, number];
  showTime: boolean;
}>) {
  const values = points.flatMap((point) =>
    point.value === null ? [] : [point.value],
  );
  // The limit always fits, and a value above it is never cut off.
  const top = Math.max(limit, ...values) * 1.1;
  const latest = points.at(-1)?.value ?? null;

  return (
    <figure aria-label={name} className="m-0 flex min-w-0 flex-col gap-1">
      <figcaption className="flex flex-wrap items-baseline justify-between gap-x-3 type-caption tabular-nums">
        <span className="font-medium text-foreground">
          {latest === null
            ? "Latest: no reading"
            : `Latest ${reading(latest, unit)}`}
        </span>
        <span className="text-muted-foreground">
          Limit {reading(limit, unit)}
        </span>
      </figcaption>
      <div className={showTime ? "h-24" : "h-16"}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart
            data={points}
            margin={{ top: 4, right: 4, bottom: 0, left: 0 }}
          >
            <CartesianGrid stroke="var(--border)" vertical={false} />
            <XAxis
              dataKey="at"
              type="number"
              domain={domain}
              tickFormatter={clock}
              minTickGap={48}
              hide={!showTime}
              stroke="var(--muted-foreground)"
              tick={{ fontSize: 10 }}
            />
            <YAxis
              domain={[0, top]}
              ticks={[0, limit]}
              tickFormatter={(value) => `${number.format(value)}${unit.trim()}`}
              width={48}
              stroke="var(--muted-foreground)"
              tick={{ fontSize: 10 }}
            />
            <Tooltip
              labelFormatter={(at) => clock(Number(at))}
              formatter={(value) => [
                reading(value == null ? null : Number(value), unit),
                name,
              ]}
              contentStyle={{
                background: "var(--popover)",
                borderColor: "var(--border)",
                color: "var(--popover-foreground)",
                fontSize: 12,
              }}
            />
            <ReferenceLine
              y={limit}
              stroke="var(--muted-foreground)"
              strokeDasharray="4 4"
            />
            <Line
              dataKey="value"
              type="monotone"
              stroke="var(--primary)"
              strokeWidth={1.5}
              connectNulls={false}
              isAnimationActive={false}
              dot={({ key, cx, cy, index }) =>
                isIsolated(points, index) ? (
                  <circle
                    key={key}
                    cx={cx}
                    cy={cy}
                    r={2.5}
                    fill="var(--primary)"
                  />
                ) : (
                  <g key={key} />
                )
              }
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </figure>
  );
}

function ComponentRow({
  component,
  status,
  samples,
  domain,
  showTime,
}: Readonly<{
  component: MetricComponentDTO;
  status: ExecutionDTO["components"][number]["status"];
  samples: MetricSampleDTO[];
  domain: [number, number];
  showTime: boolean;
}>) {
  const view = status ? nodeView(status) : null;
  const missing = samples.filter(
    (sample) => sample.cpuPercent === null && sample.memoryBytes === null,
  ).length;
  return (
    <li
      aria-label={component.name}
      className="grid grid-cols-1 gap-x-5 gap-y-2 border-b py-3 last:border-b-0 sm:grid-cols-[9rem_minmax(0,1fr)_minmax(0,1fr)]"
    >
      <div className="flex min-w-0 flex-col gap-0.5 pt-0.5">
        <span
          className="truncate type-small font-medium"
          title={component.name}
        >
          {component.name}
        </span>
        {view && (
          <span
            className={`flex items-center gap-1 type-caption ${toneText[view.tone]}`}
          >
            <StatusIcon tone={view.tone} />
            {view.label}
          </span>
        )}
        {missing > 0 && (
          <span className="type-caption text-muted-foreground">
            {missing} of {samples.length} without reading
          </span>
        )}
      </div>
      <MetricChart
        name={`${component.name} CPU, percent of one core`}
        unit="%"
        points={cpuSeries(samples)}
        limit={component.cpuLimitPercent}
        domain={domain}
        showTime={showTime}
      />
      <MetricChart
        name={`${component.name} memory, MiB`}
        unit=" MiB"
        points={memorySeries(samples)}
        limit={component.memoryLimitBytes / BYTES_PER_MIB}
        domain={domain}
        showTime={showTime}
      />
    </li>
  );
}

// CPU and memory of every node of an execution, live and after it ends, in a panel under the canvas that
// stays in view while the experiment runs. It folds to its header and, on wide screens, its top edge is
// dragged to resize it.
export default function ExecutionMetrics({
  execution,
  series,
  open,
  onOpenChange,
}: Readonly<{
  execution: ExecutionDTO;
  series: ExecutionMetricsSeries;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}>) {
  const headingId = useId();
  const bodyId = useId();
  const [height, setHeight] = useState(readPanelHeight);
  const live = execution.phase !== "RELEASED";
  const { metrics, samples, failed } = series;

  // The canvas above always keeps room to work.
  function clamp(next: number) {
    return Math.round(
      Math.max(MIN_HEIGHT, Math.min(next, window.innerHeight * 0.6)),
    );
  }

  function resizeTo(next: number) {
    const value = clamp(next);
    setHeight(value);
    store(HEIGHT_KEY, String(value));
  }

  function startResize(event: ReactPointerEvent<HTMLDivElement>) {
    event.preventDefault();
    const startY = event.clientY;
    const startHeight = height;
    const handle = event.currentTarget;
    handle.setPointerCapture(event.pointerId);
    function move(moved: PointerEvent) {
      setHeight(clamp(startHeight + startY - moved.clientY));
    }
    function end(ended: PointerEvent) {
      resizeTo(startHeight + startY - ended.clientY);
      handle.removeEventListener("pointermove", move);
      handle.removeEventListener("pointerup", end);
      handle.removeEventListener("pointercancel", end);
    }
    handle.addEventListener("pointermove", move);
    handle.addEventListener("pointerup", end);
    handle.addEventListener("pointercancel", end);
  }

  function resizeWithKeys(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowUp") resizeTo(height + KEY_STEP);
    else if (event.key === "ArrowDown") resizeTo(height - KEY_STEP);
    else return;
    event.preventDefault();
  }

  const times = samples.map((sample) => Date.parse(sample.at));
  const domain: [number, number] = [Math.min(...times), Math.max(...times)];
  const statuses = new Map(
    execution.components.map((component) => [component.key, component.status]),
  );
  const components = metrics?.components ?? [];

  function summary() {
    if (failed) return "Not updating";
    if (!metrics) return "Loading…";
    return metrics.capturing
      ? `Live · every ${metrics.intervalSeconds} s`
      : `Docker stats · every ${metrics.intervalSeconds} s`;
  }

  function emptyMessage() {
    if (failed) return "The metrics could not be loaded.";
    if (!metrics) return "Loading metrics…";
    return live
      ? "Samples appear once every component is healthy."
      : "No samples were collected for this run.";
  }

  return (
    <section
      aria-labelledby={headingId}
      style={{ "--panel-height": `${height}px` } as CSSProperties}
      className={`relative flex shrink-0 flex-col border-t bg-background ${open ? "lg:h-(--panel-height)" : ""}`}
    >
      {open && (
        <div
          role="separator"
          aria-orientation="horizontal"
          aria-label="Resize CPU and memory"
          aria-valuenow={height}
          aria-valuemin={MIN_HEIGHT}
          tabIndex={0}
          onPointerDown={startResize}
          onKeyDown={resizeWithKeys}
          className="absolute inset-x-0 -top-1 z-10 h-2 cursor-row-resize touch-none outline-none hover:bg-ring/30 focus-visible:bg-ring/50 max-lg:hidden"
        />
      )}
      <div className="flex h-10 shrink-0 items-center gap-3 px-3">
        <button
          type="button"
          aria-expanded={open}
          aria-controls={bodyId}
          onClick={() => onOpenChange(!open)}
          className="flex items-center gap-1.5 rounded-md font-display text-sm font-semibold outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ChevronUpIcon
            aria-hidden
            className={`size-4 transition-transform motion-reduce:transition-none ${open ? "rotate-180" : ""}`}
          />
          <span id={headingId}>CPU and memory</span>
        </button>
        <span
          aria-live="polite"
          className={`type-caption ${failed ? "text-destructive" : "text-muted-foreground"}`}
        >
          {summary()}
        </span>
        {components.length > 0 && (
          <span className="ml-auto type-caption text-muted-foreground">
            {components.length}{" "}
            {components.length === 1 ? "component" : "components"}
          </span>
        )}
      </div>
      {open && (
        <div id={bodyId} className="min-h-0 flex-1 overflow-y-auto px-3 pb-2">
          {metrics && samples.length > 0 ? (
            <>
              <div
                aria-hidden
                className="sticky top-0 z-[1] hidden grid-cols-[9rem_minmax(0,1fr)_minmax(0,1fr)] gap-x-5 border-b bg-background py-1.5 type-caption text-muted-foreground sm:grid"
              >
                <span>Component</span>
                <span>CPU · % of one core</span>
                <span>Memory · MiB</span>
              </div>
              <ul aria-label="Components" className="m-0 list-none p-0">
                {components.map((component, index) => (
                  <ComponentRow
                    key={component.key}
                    component={component}
                    status={statuses.get(component.key) ?? null}
                    samples={samples.filter(
                      (sample) => sample.componentKey === component.key,
                    )}
                    domain={domain}
                    showTime={index === components.length - 1}
                  />
                ))}
              </ul>
            </>
          ) : (
            <p
              aria-live="polite"
              className={`py-2 type-small ${failed ? "text-destructive" : "text-muted-foreground"}`}
            >
              {emptyMessage()}
            </p>
          )}
        </div>
      )}
    </section>
  );
}
