import { describe, expect, it } from "vitest";

import {
  counterLabel,
  counterReadings,
  formatCounter,
} from "~/features/scenario-canvas/execution-counters";
import type {
  MetricComponentDTO,
  MetricSampleDTO,
} from "~/features/scenario-canvas/model/ExecutionMetricsDTO";

const ORDERS = "3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10";
const DATABASE = "7c2e4a6b-8d0f-4b1c-9e3a-5f7b9d1c3e5a";

function component(counters: string[]): MetricComponentDTO {
  return {
    key: ORDERS,
    name: "orders-api",
    cpuLimitPercent: 50,
    memoryLimitBytes: 384 * 1024 * 1024,
    counters,
  };
}

function sample(
  id: number,
  seconds: number,
  counters: Record<string, number>,
  componentKey = ORDERS,
): MetricSampleDTO {
  return {
    id,
    componentKey,
    at: new Date(Date.UTC(2026, 10, 23, 10, 0, seconds)).toISOString(),
    cpuPercent: 1,
    memoryBytes: 1024,
    counters,
  };
}

describe("execution counters", () => {
  it("computes the latest total and per-second rate between the two latest readings of each counter", () => {
    const readings = counterReadings(
      component(["database_queries", "errors", "requests"]),
      [
        sample(1, 0, { requests: 100, errors: 0, database_queries: 50 }),
        sample(2, 5, { requests: 100 }, DATABASE),
        // Gap where scraping missed this node: the rate still spans the two samples that had a reading.
        sample(3, 5, {}),
        sample(4, 10, { requests: 200, errors: 0, database_queries: 125 }),
      ],
    );

    expect(readings).toEqual([
      { name: "requests", label: "Requests", total: 200, ratePerSecond: 10 },
      { name: "errors", label: "Errors", total: 0, ratePerSecond: 0 },
      {
        name: "database_queries",
        label: "Database queries",
        total: 125,
        ratePerSecond: 7.5,
      },
    ]);
    expect(readings.map(formatCounter)).toEqual([
      "200 · 10/s",
      "0 · 0/s",
      "125 · 7.5/s",
    ]);
  });

  it("omits the rate on a single reading or when the counter went down, and reports absent counters as No reading", () => {
    const readings = counterReadings(component(["requests", "errors"]), [
      sample(1, 0, { requests: 80 }),
      sample(2, 5, { requests: 12 }),
    ]);

    expect(readings).toEqual([
      { name: "requests", label: "Requests", total: 12, ratePerSecond: null },
      { name: "errors", label: "Errors", total: null, ratePerSecond: null },
    ]);
    expect(readings.map(formatCounter)).toEqual(["12", "No reading"]);
  });

  it("returns no readings when the component declares no counters", () => {
    expect(counterReadings(undefined, [])).toEqual([]);
    expect(counterReadings(component([]), [sample(1, 0, { requests: 1 })])).toEqual([]);
  });

  it("orders and labels downstream HTTP counters", () => {
    const readings = counterReadings(
      component([
        "downstream_timeouts",
        "errors",
        "downstream_calls",
        "requests",
        "downstream_failures",
      ]),
      [
        sample(1, 0, {
          requests: 10,
          errors: 2,
          downstream_calls: 10,
          downstream_failures: 1,
          downstream_timeouts: 1,
        }),
        sample(2, 5, {
          requests: 20,
          errors: 3,
          downstream_calls: 20,
          downstream_failures: 2,
          downstream_timeouts: 1,
        }),
      ],
    );

    expect(readings).toEqual([
      { name: "requests", label: "Requests", total: 20, ratePerSecond: 2 },
      { name: "errors", label: "Errors", total: 3, ratePerSecond: 0.2 },
      {
        name: "downstream_calls",
        label: "Downstream calls",
        total: 20,
        ratePerSecond: 2,
      },
      {
        name: "downstream_failures",
        label: "Downstream failures",
        total: 2,
        ratePerSecond: 0.2,
      },
      {
        name: "downstream_timeouts",
        label: "Downstream timeouts",
        total: 1,
        ratePerSecond: 0,
      },
    ]);
  });

  it("humanizes future counter names", () => {
    expect(counterLabel("cache_hits")).toBe("Cache hits");
  });
});
