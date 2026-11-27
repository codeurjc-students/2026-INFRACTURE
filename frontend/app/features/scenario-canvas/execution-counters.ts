import type {
  MetricComponentDTO,
  MetricSampleDTO,
} from "./model/ExecutionMetricsDTO";

const LABELS: Record<string, string> = {
  requests: "Requests",
  errors: "Errors",
  database_queries: "Database queries",
};

const ORDER = ["requests", "errors", "database_queries"];

const totalFormat = new Intl.NumberFormat("en", { maximumFractionDigits: 2 });
const rateFormat = new Intl.NumberFormat("en", { maximumFractionDigits: 1 });

export interface CounterReading {
  name: string;
  label: string;
  total: number | null;
  ratePerSecond: number | null;
}

export function counterLabel(name: string) {
  if (Object.hasOwn(LABELS, name)) return LABELS[name];
  const spaced = name.replaceAll("_", " ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

// Orders known counters consistently because JSONB does not preserve key order.
function orderedNames(names: string[]) {
  return [...names].sort((left, right) => {
    const leftIndex = ORDER.indexOf(left);
    const rightIndex = ORDER.indexOf(right);
    if (leftIndex !== -1 && rightIndex !== -1) return leftIndex - rightIndex;
    if (leftIndex !== -1) return -1;
    if (rightIndex !== -1) return 1;
    return left.localeCompare(right);
  });
}

// Summarizes each counter declared by the component: its latest cumulative total and its rate per second
// between the two latest samples that read it. If the total went down (for example after a restart), the
// rate is omitted instead of showing a negative number.
export function counterReadings(
  component: MetricComponentDTO | undefined,
  samples: MetricSampleDTO[],
): CounterReading[] {
  if (!component || component.counters.length === 0) return [];
  const own = samples.filter((sample) => sample.componentKey === component.key);
  return orderedNames(component.counters).map((name) => {
    const present = own.filter(
      (sample) =>
        sample.counters != null &&
        Object.hasOwn(sample.counters, name) &&
        Number.isFinite(sample.counters[name]),
    );
    const latest = present.at(-1);
    if (!latest) {
      return {
        name,
        label: counterLabel(name),
        total: null,
        ratePerSecond: null,
      };
    }
    const total = latest.counters[name];
    const previous = present.at(-2);
    let ratePerSecond: number | null = null;
    if (previous) {
      const delta = total - previous.counters[name];
      const seconds = (Date.parse(latest.at) - Date.parse(previous.at)) / 1000;
      if (delta >= 0 && seconds > 0) {
        ratePerSecond = delta / seconds;
      }
    }
    return {
      name,
      label: counterLabel(name),
      total,
      ratePerSecond,
    };
  });
}

export function formatCounter(reading: CounterReading) {
  if (reading.total === null) return "No reading";
  const total = totalFormat.format(reading.total);
  if (reading.ratePerSecond === null) return total;
  return `${total} · ${rateFormat.format(reading.ratePerSecond)}/s`;
}
