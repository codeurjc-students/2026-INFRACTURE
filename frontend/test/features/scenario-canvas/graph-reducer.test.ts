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
import {
  constantHttp,
  httpService,
  redis,
} from "../../fixtures/component-templates";

const empty = toCanvasState({
  revision: 1,
  components: [],
  connections: [],
  workload: null,
  issues: [],
});

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
    {
      key: "b",
      templateKey: "redis",
      type: "REDIS",
      name: "Cache",
      position: { x: 400, y: 100 },
      profile: "DEFAULT",
      parameters: {},
    },
  ],
  connections: [{ sourceKey: "a", port: "cache", targetKey: "b" }],
  workload: null,
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
        ports: [],
        capabilities: [],
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
    expect(toSaveGraph(state.nodes, state.edges, 1, null)).toEqual({
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
      connections: [],
      workload: null,
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

  it("restores a draft with the revision it started from, and keeps the work when the session expires", () => {
    const expired = graphReducer(add(empty, "a"), { type: "expired" });
    expect(expired).toMatchObject({ status: "expired", dirty: true });
    expect(expired.nodes).toHaveLength(1);

    const restored = graphReducer(toCanvasState(savedGraph), {
      type: "restored",
      draft: {
        baseRevision: 1,
        components: [{ ...savedGraph.components[0], name: "Mine" }],
        savedAt: "2026-10-31T10:00:00Z",
      },
    });
    expect(restored).toMatchObject({
      revision: 1,
      dirty: true,
      status: "idle",
    });
    expect(restored.nodes[0].data.name).toBe("Mine");
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
    expect(reloaded.nodes.map((node) => node.data.name)).toEqual([
      "Orders",
      "Cache",
    ]);
  });

  it("connects a port to another node once and sends the connection with the graph", () => {
    let state = add(add(empty, "a"), "b", redis);
    const connection = { sourceKey: "a", port: "cache", targetKey: "b" };
    state = graphReducer(
      { ...state, dirty: false },
      { type: "connected", connection },
    );
    state = graphReducer(state, { type: "connected", connection });
    state = graphReducer(state, {
      type: "connected",
      connection: { sourceKey: "a", port: "downstream", targetKey: "a" },
    });

    expect(state.dirty).toBe(true);
    expect(state.edges.map((edge) => edge.id)).toEqual(["a:cache:b"]);
    expect(toSaveGraph(state.nodes, state.edges, 1, null).connections).toEqual([
      connection,
    ]);
  });

  it("removes a connection without touching the rest, and selecting it is not a change", () => {
    const state = toCanvasState(savedGraph);

    const selected = graphReducer(state, {
      type: "edgesChanged",
      changes: [{ type: "select", id: "a:cache:b", selected: true }],
    });
    expect(selected.dirty).toBe(false);
    expect(selected.edges[0].selected).toBe(true);

    for (const removed of [
      graphReducer(state, {
        type: "edgesChanged",
        changes: [{ type: "remove", id: "a:cache:b" }],
      }),
      graphReducer(state, { type: "disconnected", id: "a:cache:b" }),
    ]) {
      expect(removed.dirty).toBe(true);
      expect(removed.edges).toEqual([]);
      expect(removed.nodes).toHaveLength(2);
    }
  });

  it("removes the connections of a deleted node", () => {
    const state = toCanvasState(savedGraph);

    expect(graphReducer(state, { type: "removed", key: "b" }).edges).toEqual(
      [],
    );
    expect(
      graphReducer(state, {
        type: "nodesChanged",
        changes: [{ type: "remove", id: "a" }],
      }).edges,
    ).toEqual([]);
  });

  it("keeps connections when the profile changes", () => {
    const state = graphReducer(toCanvasState(savedGraph), {
      type: "profileChanged",
      key: "a",
      profile: httpService.contract.profiles[0],
    });

    expect(state.nodes[0].data.profile).toBe("STATELESS");
    expect(state.edges.map((edge) => edge.id)).toEqual(["a:cache:b"]);
  });

  it("restores the connections of a draft, and none from a draft saved before connections existed", () => {
    const draft = {
      baseRevision: 1,
      components: savedGraph.components,
      savedAt: "2026-10-31T10:00:00Z",
    };

    expect(
      graphReducer(empty, {
        type: "restored",
        draft: { ...draft, connections: savedGraph.connections },
      }).edges.map((edge) => edge.id),
    ).toEqual(["a:cache:b"]);
    expect(graphReducer(empty, { type: "restored", draft }).edges).toEqual([]);
  });

  it("adds a load without target from the profile's initial values and saves it with the graph", () => {
    const state = graphReducer(add(empty, "a"), {
      type: "workloadAdded",
      profile: constantHttp,
      seed: 42,
    });

    expect(state.dirty).toBe(true);
    expect(state.workload).toEqual({
      targetKey: null,
      profile: "CONSTANT_HTTP",
      parameters: {
        requestsPerSecond: 5,
        virtualUsers: 2,
        durationSeconds: 60,
      },
      seed: 42,
    });
    expect(
      toSaveGraph(state.nodes, state.edges, 1, state.workload).workload,
    ).toEqual(state.workload);
  });

  it("changes the target, numbers and seed of the load, and removes it", () => {
    let state = graphReducer(add(empty, "a"), {
      type: "workloadAdded",
      profile: constantHttp,
      seed: 42,
    });
    state = graphReducer(state, {
      type: "workloadTargetChanged",
      targetKey: "a",
    });
    state = graphReducer(state, {
      type: "workloadParameterChanged",
      name: "requestsPerSecond",
      value: 20,
    });
    state = graphReducer(state, {
      type: "workloadParameterChanged",
      name: "virtualUsers",
      value: undefined,
    });
    state = graphReducer(state, { type: "workloadSeedChanged", seed: 7 });

    expect(state.workload).toEqual({
      targetKey: "a",
      profile: "CONSTANT_HTTP",
      parameters: { requestsPerSecond: 20, durationSeconds: 60 },
      seed: 7,
    });
    expect(
      graphReducer(state, { type: "workloadRemoved" }).workload,
    ).toBeNull();
  });

  it("ignores load changes when there is no load", () => {
    expect(
      graphReducer(empty, { type: "workloadTargetChanged", targetKey: "a" }),
    ).toBe(empty);
    expect(graphReducer(empty, { type: "workloadRemoved" })).toBe(empty);
  });

  it("keeps the load without target when its target is removed, by the inspector or the keyboard", () => {
    const loaded = toCanvasState({
      ...savedGraph,
      workload: {
        targetKey: "a",
        profile: "CONSTANT_HTTP",
        parameters: {},
        seed: 1,
      },
    });

    expect(
      graphReducer(loaded, { type: "removed", key: "a" }).workload,
    ).toEqual({
      targetKey: null,
      profile: "CONSTANT_HTTP",
      parameters: {},
      seed: 1,
    });
    expect(
      graphReducer(loaded, {
        type: "nodesChanged",
        changes: [{ type: "remove", id: "a" }],
      }).workload?.targetKey,
    ).toBeNull();
    expect(
      graphReducer(loaded, { type: "removed", key: "b" }).workload?.targetKey,
    ).toBe("a");
  });

  it("restores the load of a draft, and keeps the current one for a draft saved before loads existed", () => {
    const loaded = toCanvasState({
      ...savedGraph,
      workload: {
        targetKey: "a",
        profile: "CONSTANT_HTTP",
        parameters: {},
        seed: 1,
      },
    });
    const draft = {
      baseRevision: 1,
      components: savedGraph.components,
      connections: savedGraph.connections,
      savedAt: "2026-11-09T10:00:00Z",
    };

    expect(
      graphReducer(loaded, {
        type: "restored",
        draft: { ...draft, workload: null },
      }).workload,
    ).toBeNull();
    expect(graphReducer(loaded, { type: "restored", draft }).workload).toEqual(
      loaded.workload,
    );
  });
});
