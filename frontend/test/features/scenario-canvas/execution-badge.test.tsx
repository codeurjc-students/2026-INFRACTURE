import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ExecutionBadge } from "~/features/scenario-canvas/components/execution-badge";
import type { ExecutionDTO } from "~/features/scenario-canvas/model/ExecutionDTO";

function running(secondsLeft: number | null): ExecutionDTO {
  return {
    id: 12,
    scenarioId: 3,
    scenarioRevision: 2,
    phase: "RUNNING",
    stopCause: null,
    outcome: null,
    failureReason: null,
    cleanupFailures: 0,
    cleanupError: null,
    cleanupRetryNeeded: false,
    createdAt: "2026-11-13T10:00:00Z",
    deadlineAt: "2026-11-13T10:30:00Z",
    secondsLeft,
    releasedAt: null,
    components: [],
    workload: null,
  };
}

describe("execution badge", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("counts down each second between polls and never goes below zero", () => {
    render(<ExecutionBadge execution={running(2)} />);
    const timer = screen.getByRole("timer", { name: "Time left" });
    expect(timer).toHaveTextContent("0:02 left");

    act(() => vi.advanceTimersByTime(1000));
    expect(timer).toHaveTextContent("0:01 left");
    act(() => vi.advanceTimersByTime(5000));
    expect(timer).toHaveTextContent("0:00 left");
  });

  it("follows the server's figure when a new one arrives", () => {
    const { rerender } = render(<ExecutionBadge execution={running(600)} />);
    act(() => vi.advanceTimersByTime(3000));
    expect(screen.getByRole("timer")).toHaveTextContent("9:57 left");

    rerender(<ExecutionBadge execution={running(1200)} />);

    expect(screen.getByRole("timer")).toHaveTextContent("20:00 left");
  });

  // The countdown changes every second, so it stays out of the live region the state is read from.
  it("keeps the countdown out of the announced state and hides it without a deadline", () => {
    const { rerender } = render(<ExecutionBadge execution={running(90)} />);
    expect(screen.getByRole("status")).toHaveTextContent("Running");
    expect(screen.getByRole("status")).not.toHaveTextContent("left");

    rerender(
      <ExecutionBadge
        execution={{ ...running(null), stopCause: "TIME_LIMIT" }}
      />,
    );

    expect(screen.queryByRole("timer")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Time limit reached, stopping",
    );
  });
});
