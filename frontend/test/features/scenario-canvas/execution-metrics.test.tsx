import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import "../../react-flow-mocks";
import { getExecutionMetrics } from "~/features/scenario-canvas/api/execution-service";
import ExecutionMetrics, {
  cpuSeries,
  memorySeries,
  readPanelHeight,
  reading,
} from "~/features/scenario-canvas/components/execution-metrics";
import type { ExecutionDTO } from "~/features/scenario-canvas/model/ExecutionDTO";
import type {
  ExecutionMetricsDTO,
  MetricSampleDTO,
} from "~/features/scenario-canvas/model/ExecutionMetricsDTO";
import { useExecutionMetrics } from "~/features/scenario-canvas/use-execution-metrics";

const ORDERS = "3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10";
const USERS = "0b9c8d7e-6f5a-4b3c-8d2e-1f0a9b8c7d6e";
const MIB = 1024 * 1024;

function sample(
  id: number,
  cpuPercent: number | null,
  memoryBytes: number | null,
  componentKey = ORDERS,
  counters: Record<string, number> = {},
) {
  return {
    id,
    componentKey,
    at: new Date(Date.UTC(2026, 10, 23, 10, 0, id * 5)).toISOString(),
    cpuPercent,
    memoryBytes,
    counters,
  } satisfies MetricSampleDTO;
}

const COMPONENTS = [
  {
    key: ORDERS,
    name: "orders-api",
    cpuLimitPercent: 50,
    memoryLimitBytes: 384 * MIB,
    counters: ["requests", "errors"],
  },
  {
    key: USERS,
    name: "users-api",
    cpuLimitPercent: 50,
    memoryLimitBytes: 384 * MIB,
    counters: ["requests", "errors"],
  },
];

function page(
  samples: MetricSampleDTO[],
  fields: Partial<ExecutionMetricsDTO> = {},
): ExecutionMetricsDTO {
  return {
    source: "DOCKER_STATS",
    cpuUnit: "PERCENT_OF_ONE_CORE",
    memoryUnit: "BYTES",
    intervalSeconds: 5,
    capturing: true,
    hasMore: false,
    nextAfter: samples.at(-1)?.id ?? 0,
    components: COMPONENTS,
    samples,
    ...fields,
  };
}

function execution(fields: Partial<ExecutionDTO> = {}) {
  return {
    id: 12,
    phase: "RUNNING",
    components: [
      { key: ORDERS, name: "orders-api", type: "HTTP_SERVICE", status: "HEALTHY", response: null },
      { key: USERS, name: "users-api", type: "HTTP_SERVICE", status: "UNHEALTHY", response: null },
    ],
    ...fields,
  } as ExecutionDTO;
}

function Panel({
  run = execution(),
  open = true,
  onOpenChange = () => {},
}: Readonly<{ run?: ExecutionDTO; open?: boolean; onOpenChange?: (open: boolean) => void }>) {
  const series = useExecutionMetrics("7", "3", run.id, run.phase !== "RELEASED");
  return (
    <ExecutionMetrics
      execution={run}
      series={series}
      open={open}
      onOpenChange={onOpenChange}
    />
  );
}

const fetchMock = vi.fn();
const urls = () => fetchMock.mock.calls.map(([url]) => String(url));
const flush = () => act(() => vi.advanceTimersByTimeAsync(0));
const wait = (ms: number) => act(() => vi.advanceTimersByTimeAsync(ms));

beforeEach(() => {
  vi.useFakeTimers();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  localStorage.clear();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("execution metrics", () => {
  it("asks for every node of the execution at once and continues after a cursor", async () => {
    fetchMock.mockImplementation(async () => Response.json(page([])));

    await getExecutionMetrics("7", "3", 12);
    await getExecutionMetrics("7", "3", 12, 40);

    const base = "/api/v1/projects/7/scenarios/3/executions/12/metrics";
    expect(urls()).toEqual([base, `${base}?after=40`]);
  });

  it("keeps a real zero and leaves a gap where there was no reading", () => {
    const samples = [sample(1, 0, 0), sample(2, null, null), sample(3, 25.5, 2 * MIB)];

    expect(cpuSeries(samples).map((point) => point.value)).toEqual([0, null, 25.5]);
    expect(memorySeries(samples).map((point) => point.value)).toEqual([0, null, 2]);
    expect(cpuSeries(samples)[0].at).toBe(Date.parse(samples[0].at));
  });

  it("describes a gap as no reading and a real zero as zero", () => {
    expect(reading(null, "%")).toBe("No reading");
    expect(reading(0, "%")).toBe("0%");
    expect(reading(127.894, " MiB")).toBe("127.89 MiB");
  });

  it("reads every page, then only what is new while the execution is live", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json(page([sample(1, 1, 1)], { hasMore: true })))
      .mockResolvedValueOnce(Response.json(page([sample(2, 2, 2, USERS)])))
      .mockResolvedValueOnce(Response.json(page([sample(3, 3, 3)])));

    const { result } = renderHook(() => useExecutionMetrics("7", "3", 12, true));
    await flush();

    expect(result.current.samples.map((item) => item.id)).toEqual([1, 2]);
    expect(urls()[1]).toContain("?after=1");

    await wait(5_000);

    expect(urls()[2]).toContain("?after=2");
    expect(result.current.samples.map((item) => item.id)).toEqual([1, 2, 3]);
  });

  it("makes one last read when the execution ends and then stops asking", async () => {
    fetchMock.mockImplementation(async () =>
      Response.json(page([], { capturing: false })),
    );
    const { rerender } = renderHook(
      ({ live }) => useExecutionMetrics("7", "3", 12, live),
      { initialProps: { live: true } },
    );
    await flush();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    rerender({ live: false });
    await wait(5_000);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await wait(30_000);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("does not ask while the tab is hidden and catches up when it returns", async () => {
    fetchMock.mockImplementation(async () => Response.json(page([])));
    const visibility = vi.spyOn(document, "visibilityState", "get");
    visibility.mockReturnValue("hidden");
    renderHook(() => useExecutionMetrics("7", "3", 12, true));
    await flush();
    const before = fetchMock.mock.calls.length;

    await wait(30_000);
    expect(fetchMock).toHaveBeenCalledTimes(before);

    visibility.mockReturnValue("visible");
    document.dispatchEvent(new Event("visibilitychange"));
    await flush();
    expect(fetchMock).toHaveBeenCalledTimes(before + 1);
    visibility.mockRestore();
  });

  it("asks nothing when there is no execution yet", async () => {
    const { result } = renderHook(() => useExecutionMetrics("7", "3", null, false));
    await flush();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(result.current).toEqual({ metrics: null, samples: [], failed: false });
  });

  it("starts again from the beginning for another execution", async () => {
    fetchMock.mockImplementation(async (url: string) =>
      Response.json(
        url.includes("/executions/13/")
          ? page([sample(9, 9, 9)])
          : page([sample(1, 1, 1)]),
      ),
    );
    const { result, rerender } = renderHook(
      ({ id }) => useExecutionMetrics("7", "3", id, true),
      { initialProps: { id: 12 } },
    );
    await flush();

    rerender({ id: 13 });
    await flush();

    expect(urls().at(-1)).toBe("/api/v1/projects/7/scenarios/3/executions/13/metrics");
    expect(result.current.samples.map((item) => item.id)).toEqual([9]);
  });

  it("keeps what it has when a read fails and recovers on the next one", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json(page([sample(1, 1, 1)])))
      .mockResolvedValueOnce(new Response("", { status: 500 }))
      .mockResolvedValueOnce(Response.json(page([sample(2, 2, 2)])));
    const { result } = renderHook(() => useExecutionMetrics("7", "3", 12, true));
    await flush();

    await wait(5_000);
    expect(result.current.failed).toBe(true);
    expect(result.current.samples).toHaveLength(1);

    await wait(5_000);
    expect(result.current.failed).toBe(false);
    expect(result.current.samples).toHaveLength(2);
  });

  it("shows one row per node with its own series, state, units and limits", async () => {
    fetchMock.mockResolvedValue(
      Response.json(
        page([
          sample(1, 0, MIB),
          sample(2, 4, 3 * MIB, USERS),
          sample(3, null, null),
          sample(4, 25.5, 2 * MIB),
        ]),
      ),
    );

    render(<Panel />);
    await flush();

    const rows = within(screen.getByRole("list", { name: "Components" })).getAllByRole("listitem");
    expect(rows.map((row) => row.getAttribute("aria-label"))).toEqual(["orders-api", "users-api"]);

    const orders = within(rows[0]);
    expect(orders.getByText("Healthy")).toBeVisible();
    expect(orders.getByText("1 of 3 without reading")).toBeVisible();
    const ordersCpu = orders.getByRole("figure", { name: "orders-api CPU, percent of one core" });
    expect(ordersCpu).toHaveTextContent("Latest 25.5%");
    expect(ordersCpu).toHaveTextContent("Limit 50%");
    const ordersMemory = orders.getByRole("figure", { name: "orders-api memory, MiB" });
    expect(ordersMemory).toHaveTextContent("Latest 2 MiB");
    expect(ordersMemory).toHaveTextContent("Limit 384 MiB");

    const users = within(rows[1]);
    expect(users.getByText("Unhealthy")).toBeVisible();
    expect(users.getByRole("figure", { name: "users-api CPU, percent of one core" })).toHaveTextContent("Latest 4%");
    expect(users.queryByText(/without reading/)).not.toBeInTheDocument();

    expect(screen.getByText("Live · every 5 s")).toBeVisible();
    expect(screen.getByText("2 components")).toBeVisible();
  });

  it("shows a real zero as zero and a missing last reading as missing", async () => {
    fetchMock.mockResolvedValueOnce(Response.json(page([sample(1, 0, 0)])));
    const { unmount } = render(<Panel />);
    await flush();
    expect(
      screen.getByRole("figure", { name: "orders-api CPU, percent of one core" }),
    ).toHaveTextContent("Latest 0%");
    unmount();

    fetchMock.mockResolvedValueOnce(Response.json(page([sample(2, null, null)])));
    render(<Panel />);
    await flush();
    expect(
      screen.getByRole("figure", { name: "orders-api CPU, percent of one core" }),
    ).toHaveTextContent("Latest: no reading");
  });

  it("tells waiting, nothing collected and a failed load apart", async () => {
    fetchMock.mockResolvedValueOnce(Response.json(page([], { capturing: false })));
    const first = render(<Panel run={execution({ phase: "STARTING" })} />);
    expect(screen.getByText("Loading metrics…")).toBeVisible();
    await flush();
    expect(screen.getByText("Samples appear once every component is healthy.")).toBeVisible();
    first.unmount();

    fetchMock.mockResolvedValueOnce(Response.json(page([], { capturing: false })));
    const second = render(<Panel run={execution({ phase: "RELEASED" })} />);
    await flush();
    expect(screen.getByText("No samples were collected for this run.")).toBeVisible();
    second.unmount();

    fetchMock.mockResolvedValueOnce(new Response("", { status: 500 }));
    render(<Panel run={execution({ phase: "RELEASED" })} />);
    await flush();
    expect(screen.getByText("The metrics could not be loaded.")).toBeVisible();
    expect(screen.getByText("Not updating")).toBeVisible();
  });

  it("folds to its header and opens again", async () => {
    fetchMock.mockResolvedValue(Response.json(page([sample(1, 1, MIB)])));
    const onOpenChange = vi.fn();
    const { rerender } = render(<Panel onOpenChange={onOpenChange} />);
    await flush();

    const toggle = screen.getByRole("button", { name: "CPU and memory" });
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(toggle);
    expect(onOpenChange).toHaveBeenCalledWith(false);

    rerender(<Panel open={false} onOpenChange={onOpenChange} />);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("list", { name: "Components" })).not.toBeInTheDocument();
    expect(screen.queryByRole("separator")).not.toBeInTheDocument();
    expect(screen.getByText("Live · every 5 s")).toBeVisible();

    fireEvent.click(toggle);
    expect(onOpenChange).toHaveBeenLastCalledWith(true);
  });

  it("resizes from the keyboard and remembers the height", async () => {
    fetchMock.mockResolvedValue(Response.json(page([])));
    const { unmount } = render(<Panel />);
    await flush();

    const handle = screen.getByRole("separator", { name: "Resize CPU and memory" });
    expect(handle).toHaveAttribute("aria-valuenow", "280");
    fireEvent.keyDown(handle, { key: "ArrowUp" });
    expect(handle).toHaveAttribute("aria-valuenow", "304");
    fireEvent.keyDown(handle, { key: "ArrowDown" });
    fireEvent.keyDown(handle, { key: "ArrowDown" });
    expect(handle).toHaveAttribute("aria-valuenow", "256");
    unmount();

    expect(readPanelHeight()).toBe(256);
    render(<Panel />);
    expect(screen.getByRole("separator")).toHaveAttribute("aria-valuenow", "256");
  });
});
