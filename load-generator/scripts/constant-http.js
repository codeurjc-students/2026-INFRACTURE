// CONSTANT_HTTP (ADR 0016): GET TARGET_URL at a fixed rate, with at most VIRTUAL_USERS requests in flight.
// The plan passes the target URL and numbers only; everything else is fixed here.
import http from "k6/http";

function number(name) {
  const value = Number(__ENV[name]);
  if (!Number.isInteger(value)) {
    throw new Error(`${name} must be an integer`);
  }
  return value;
}

const virtualUsers = number("VIRTUAL_USERS");

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ["avg", "p(95)", "max"],
  scenarios: {
    load: {
      executor: "constant-arrival-rate",
      rate: number("REQUESTS_PER_SECOND"),
      timeUnit: "1s",
      duration: `${number("DURATION_SECONDS")}s`,
      preAllocatedVUs: virtualUsers,
      maxVUs: virtualUsers,
      // Stop waits at most this long for requests in flight.
      gracefulStop: "5s",
    },
  },
};

// RANDOM_SEED is not used: every request is the same.
export default function () {
  http.get(__ENV.TARGET_URL, { timeout: "10s" });
}

// One line the backend can find in the logs. A metric k6 never recorded stays null instead of 0.
export function handleSummary(data) {
  const value = (metric, stat) => data.metrics[metric]?.values[stat] ?? null;
  const summary = {
    requests: value("http_reqs", "count") ?? 0,
    failedRequests: value("http_req_failed", "passes") ?? 0,
    droppedIterations: value("dropped_iterations", "count") ?? 0,
    averageLatencyMs: value("http_req_duration", "avg"),
    p95LatencyMs: value("http_req_duration", "p(95)"),
  };
  return { stdout: `INFRACTURE_SUMMARY ${JSON.stringify(summary)}\n` };
}
