import { describe, expect, it } from "vitest";

import {
  BOARD,
  graphReducer,
  MAX_COMPONENTS,
  toCanvasState,
  toSaveGraph,
  type CanvasState,
} from "~/features/scenario-canvas/graph-reducer";
import type { ScenarioGraphDTO } from "~/features/scenario-canvas/model/ScenarioGraphDTO";
import { httpService, redis } from "../../fixtures/component-templates";

const empty = toCanvasState({ revision: 1, components: [], issues: [] });

function add(state: CanvasState, key: string, template = httpService) {
  return graphReducer(state, {
    type: "added",
    key,
    template,
    position: { x: 100, y: 100 },
  });
}

const savedGraph: ScenarioGraphDTO = {
  revision: 2,
  components: [
    {
      key: "a",
      templateKey: "http-service",
      type: "HTTP_SERVICE",
      name: "Orders",
      position: { x: 100, y: 100 },
      profile: "STATELESS",
      parameters: { responseDelayMs: 9000 },
    },
  ],
  issues: [
    {
      severity: "ERROR",
      code: "PARAMETER_OUT_OF_RANGE",
      message: "responseDelayMs must be between 0 and 5000",
      target: { kind: "COMPONENT", key: "a" },
      field: "responseDelayMs",
    },
  ],
};

describe("graph reducer", () => {
  it("adds independent nodes from one template with a copy of its initial configuration", () => {
    let state = add(add(empty, "a"), "b");
    state = graphReducer(state, {
      type: "parameterChanged",
      key: "a",
      name: "responseDelayMs",
      value: 250,
    });

    const [first, second] = state.nodes;
    expect(first.data.parameters).toEqual({ responseDelayMs: 250 });
    expect(second.data.parameters).toEqual({ responseDelayMs: 0 });
    expect(httpService.initialConfiguration.parameters).toEqual({
      responseDelayMs: 0,
    });
    expect([first.data.name, second.data.name]).toEqual([
      "HTTP Service",
      "HTTP Service 2",
    ]);
    expect(second.position).toEqual({ x: 100, y: 260 });
    expect([first.selected, second.selected]).toEqual([false, true]);
    expect(state.dirty).toBe(true);
  });

  it("keeps saved and new nodes on the board", () => {
    const outside = toCanvasState({
      ...savedGraph,
      components: [
        { ...savedGraph.components[0], position: { x: -50, y: 9000 } },
      ],
    });
    expect(outside.nodes[0].position).toEqual({ x: 0, y: BOARD.height - 160 });

    const added = graphReducer(empty, {
      type: "added",
      key: "far",
      template: redis,
      position: { x: 99999, y: -10 },
    });
    expect(added.nodes[0].position).toEqual({ x: BOARD.width - 224, y: 0 });
  });

  it("stops adding nodes at the component limit", () => {
    let state = empty;
    for (let index = 0; index < MAX_COMPONENTS + 1; index++) {
      state = add(state, `node-${index}`, redis);
    }
    expect(state.nodes).toHaveLength(MAX_COMPONENTS);
  });

  it("marks moves and removals as changes, but not selection", () => {
    const state = toCanvasState(savedGraph);

    const selected = graphReducer(state, {
      type: "nodesChanged",
      changes: [{ type: "select", id: "a", selected: true }],
    });
    expect(selected.dirty).toBe(false);
    expect(selected.nodes[0].selected).toBe(true);

    const moved = graphReducer(state, {
      type: "nodesChanged",
      changes: [{ type: "position", id: "a", position: { x: 5, y: 6 } }],
    });
    expect(moved.dirty).toBe(true);
    expect(moved.nodes[0].position).toEqual({ x: 5, y: 6 });
  });

  it("resets parameters to the new profile, keeping the shared ones", () => {
    const state = graphReducer(add(empty, "a"), {
      type: "profileChanged",
      key: "a",
      profile: {
        name: "SLOW",
        parameters: [
          { name: "responseDelayMs", min: 0, max: 5000 },
          { name: "errorRate", min: 0, max: 100 },
        ],
      },
    });
    expect(state.nodes[0].data).toMatchObject({
      profile: "SLOW",
      parameters: { responseDelayMs: 0, errorRate: 0 },
    });
  });

  it("sends the graph without node types or selection", () => {
    const state = graphReducer(add(empty, "a"), {
      type: "renamed",
      key: "a",
      name: "Orders",
    });
    expect(toSaveGraph(state.nodes, 1)).toEqual({
      revision: 1,
      components: [
        {
          key: "a",
          templateKey: "http-service",
          name: "Orders",
          position: { x: 100, y: 100 },
          profile: "STATELESS",
          parameters: { responseDelayMs: 0 },
        },
      ],
    });
  });

  it("takes the saved graph, its revision and issues after a save", () => {
    let state = graphReducer(add(empty, "a"), { type: "saveStarted" });
    expect(state).toMatchObject({ status: "saving", dirty: false });

    state = graphReducer(state, { type: "saved", graph: savedGraph });
    expect(state).toMatchObject({ revision: 2, dirty: false, status: "idle" });
    expect(state.issues).toHaveLength(1);
    expect(state.nodes[0]).toMatchObject({
      id: "a",
      selected: true,
      data: { name: "Orders" },
    });
  });

  it("keeps edits made while the save was running", () => {
    let state = graphReducer(add(empty, "a"), { type: "saveStarted" });
    state = graphReducer(state, { type: "renamed", key: "a", name: "Newer" });
    state = graphReducer(state, { type: "saved", graph: savedGraph });

    expect(state).toMatchObject({ revision: 2, dirty: true });
    expect(state.nodes[0].data.name).toBe("Newer");
  });

  it("keeps the local changes when a save fails or conflicts, and drops them on reload", () => {
    const edited = graphReducer(add(empty, "a"), { type: "saveStarted" });

    const failed = graphReducer(edited, {
      type: "saveFailed",
      message: "Offline.",
    });
    expect(failed).toMatchObject({
      status: "failed",
      dirty: true,
      error: "Offline.",
    });
    expect(failed.nodes).toHaveLength(1);

    const conflict = graphReducer(edited, {
      type: "conflicted",
      message: "Saved after revision 1.",
    });
    expect(conflict).toMatchObject({
      status: "conflict",
      dirty: true,
      revision: 1,
    });
    expect(conflict.nodes).toHaveLength(1);

    const reloaded = graphReducer(conflict, {
      type: "reloaded",
      graph: savedGraph,
    });
    expect(reloaded).toMatchObject({
      status: "idle",
      dirty: false,
      revision: 2,
    });
    expect(reloaded.nodes.map((node) => node.data.name)).toEqual(["Orders"]);
  });
});
