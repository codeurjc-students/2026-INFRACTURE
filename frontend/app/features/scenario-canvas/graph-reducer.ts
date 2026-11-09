import {
  applyEdgeChanges,
  applyNodeChanges,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
} from "@xyflow/react";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import type { WorkloadProfileDTO } from "~/features/component-catalogue/model/WorkloadProfileDTO";
import type { TemplateContractDTO } from "~/features/component-catalogue/model/TemplateContractDTO";
import { connectionId, toConnection, toEdge } from "./connections";
import type { ComponentDTO } from "./model/ComponentDTO";
import type { ConnectionDTO } from "./model/ConnectionDTO";
import type { PositionDTO } from "./model/PositionDTO";
import type { SaveScenarioGraphDTO } from "./model/SaveScenarioGraphDTO";
import type { ScenarioGraphDTO } from "./model/ScenarioGraphDTO";
import type { GraphDraft } from "./draft-storage";
import type { ValidationIssueDTO } from "./model/ValidationIssueDTO";
import type { WorkloadDTO } from "./model/WorkloadDTO";

// Same limit as the backend.
export const MAX_COMPONENTS = 50;
// Step used to move a new node down when its spot is taken.
const NODE_SPACING = { x: 240, y: 160 };
// The board a scenario is drawn on: nodes and the view never leave it.
export const BOARD = { width: 4000, height: 3000 };
// Approximate size of a drawn node, so the whole node stays on the board.
const NODE_SIZE = { width: 224, height: 160 };

// Moves a position inside the board. The API accepts any position, so saved ones are checked too.
export function onBoard({ x, y }: PositionDTO): PositionDTO {
  return {
    x: Math.min(Math.max(x, 0), BOARD.width - NODE_SIZE.width),
    y: Math.min(Math.max(y, 0), BOARD.height - NODE_SIZE.height),
  };
}

// A node's data is the component DTO without key and position, because React Flow keeps those as the node id and position.
export type ComponentNodeData = Omit<ComponentDTO, "key" | "position">;
// A node as React Flow sees it: id, position, selection and our data.
export type ComponentNode = Node<ComponentNodeData, "component">;

// Everything the editor knows about the graph.
export interface CanvasState {
  // Last saved revision; it goes back with the next save to detect a save from another tab.
  revision: number;
  nodes: ComponentNode[];
  // The connections, as React Flow edges: the source handle is the port.
  edges: Edge[];
  // The scenario load, or null when it has none. It is not a node (ADR 0012).
  workload: WorkloadDTO | null;
  // Contract issues from the last read or save.
  issues: ValidationIssueDTO[];
  // Local changes which have not been saved to the server yet.
  dirty: boolean;
  // Drives the save button and the error, conflict and session alerts.
  status: "idle" | "saving" | "failed" | "conflict" | "expired";
  // Message shown when a save fails or conflicts.
  error: string | null;
}

// Every change the editor can go through; graphReducer handles each one.
export type CanvasAction =
  | { type: "nodesChanged"; changes: NodeChange<ComponentNode>[] }
  | {
      type: "added";
      key: string;
      template: ComponentTemplateDTO;
      position: PositionDTO;
    }
  | { type: "renamed"; key: string; name: string }
  | {
      type: "profileChanged";
      key: string;
      profile: TemplateContractDTO["profiles"][number];
    }
  | {
      type: "parameterChanged";
      key: string;
      name: string;
      value: number | undefined;
    }
  | { type: "removed"; key: string }
  | { type: "workloadAdded"; profile: WorkloadProfileDTO; seed: number }
  | { type: "workloadProfileChanged"; profile: WorkloadProfileDTO }
  | { type: "workloadTargetChanged"; targetKey: string | null }
  | {
      type: "workloadParameterChanged";
      name: string;
      value: number | undefined;
    }
  | { type: "workloadSeedChanged"; seed: number }
  | { type: "workloadRemoved" }
  | { type: "edgesChanged"; changes: EdgeChange[] }
  | { type: "connected"; connection: ConnectionDTO }
  | { type: "disconnected"; id: string }
  | { type: "saveStarted" }
  | { type: "saved"; graph: ScenarioGraphDTO }
  | { type: "saveFailed"; message: string }
  | { type: "conflicted"; message: string }
  | { type: "expired" }
  | { type: "reloaded"; graph: ScenarioGraphDTO }
  | { type: "restored"; draft: GraphDraft };

// Turns a component from the backend into a React Flow node.
function toNode(component: ComponentDTO, selected = false): ComponentNode {
  const { key, position, ...data } = component;
  return {
    id: key,
    type: "component",
    position: onBoard(position),
    selected,
    data,
  };
}

// Initial state built from the graph the route loaded.
export function toCanvasState(graph: ScenarioGraphDTO): CanvasState {
  return {
    revision: graph.revision,
    nodes: graph.components.map((component) => toNode(component)),
    edges: graph.connections.map((connection) => toEdge(connection)),
    workload: graph.workload,
    issues: graph.issues,
    dirty: false,
    status: "idle",
    error: null,
  };
}

// The inverse of toNode: the nodes back as components, the shape kept in the local draft.
export function toComponents(nodes: ComponentNode[]): ComponentDTO[] {
  return nodes.map(({ id, position, data }) => ({
    ...data,
    key: id,
    position: { x: position.x, y: position.y },
  }));
}

// Builds the body of the save request: the backend accepts neither the type nor the selection.
export function toSaveGraph(
  nodes: ComponentNode[],
  edges: Edge[],
  revision: number,
  workload: WorkloadDTO | null,
): SaveScenarioGraphDTO {
  return {
    revision,
    components: toComponents(nodes).map(
      ({ key, templateKey, name, position, profile, parameters }) => ({
        key,
        templateKey,
        name,
        position,
        profile,
        parameters,
      }),
    ),
    connections: edges.map(toConnection),
    workload,
  };
}

// Connections only between nodes that are still on the board.
function withoutDangling(edges: Edge[], nodes: ComponentNode[]) {
  const keys = new Set(nodes.map((node) => node.id));
  return edges.filter((edge) => keys.has(edge.source) && keys.has(edge.target));
}

// A load whose target left the board keeps its settings without a target, as the backend does.
function withoutMissingTarget(
  workload: WorkloadDTO | null,
  nodes: ComponentNode[],
): WorkloadDTO | null {
  if (
    workload?.targetKey &&
    !nodes.some((node) => node.id === workload.targetKey)
  ) {
    return { ...workload, targetKey: null };
  }
  return workload;
}

// Applies a change to the load and marks the graph as changed. Without a load there is nothing to change.
function editWorkload(
  state: CanvasState,
  change: (workload: WorkloadDTO) => WorkloadDTO,
): CanvasState {
  return state.workload
    ? { ...state, dirty: true, workload: change(state.workload) }
    : state;
}

// Picks a free name: "HTTP Service", then "HTTP Service 2", "HTTP Service 3"...
function nextName(nodes: ComponentNode[], base: string) {
  const names = new Set(nodes.map((node) => node.data.name));
  let name = base;
  let index = 2;
  while (names.has(name)) {
    name = `${base} ${index}`;
    index += 1;
  }
  return name;
}

// Places a new node below any node it would cover.
function freePosition(nodes: ComponentNode[], position: PositionDTO) {
  const { x } = position;
  let { y } = position;
  while (
    nodes.some(
      (node) =>
        Math.abs(node.position.x - x) < NODE_SPACING.x &&
        Math.abs(node.position.y - y) < NODE_SPACING.y,
    )
  ) {
    y += NODE_SPACING.y;
  }
  return { x, y };
}

// Applies a change to one node's data and marks the graph as changed.
function edit(
  state: CanvasState,
  key: string,
  change: (data: ComponentNodeData) => ComponentNodeData,
): CanvasState {
  return {
    ...state,
    dirty: true,
    nodes: state.nodes.map((node) =>
      node.id === key ? { ...node, data: change(node.data) } : node,
    ),
  };
}

// Pure function: receives the current state and an action and returns a new state.
// It never changes the old state; React only redraws when it gets a new object.
export function graphReducer(
  state: CanvasState,
  action: CanvasAction,
): CanvasState {
  switch (action.type) {
    case "nodesChanged": {
      // React Flow reports moves, selections and deletions. Selecting is not a change to save.
      const edited = action.changes.some(
        (change) => change.type === "position" || change.type === "remove",
      );
      const nodes = applyNodeChanges(action.changes, state.nodes);
      return {
        ...state,
        dirty: state.dirty || edited,
        nodes,
        edges: withoutDangling(state.edges, nodes),
        workload: withoutMissingTarget(state.workload, nodes),
      };
    }
    case "added": {
      // The new node starts selected so its configuration panel opens.
      if (state.nodes.length >= MAX_COMPONENTS) {
        return state;
      }
      const { template } = action;
      const node: ComponentNode = {
        id: action.key,
        type: "component",
        position: onBoard(freePosition(state.nodes, onBoard(action.position))),
        selected: true,
        data: {
          templateKey: template.key,
          type: template.type,
          name: nextName(state.nodes, template.name),
          profile: template.initialConfiguration.profile,
          // A copy, so editing this node never changes the template or another node.
          parameters: { ...template.initialConfiguration.parameters },
        },
      };
      return {
        ...state,
        dirty: true,
        nodes: [
          ...state.nodes.map((other) => ({ ...other, selected: false })),
          node,
        ],
      };
    }
    case "renamed":
      return edit(state, action.key, (data) => ({
        ...data,
        name: action.name,
      }));
    case "profileChanged":
      // Keeps the values the new profile shares; new parameters start at their minimum.
      return edit(state, action.key, (data) => ({
        ...data,
        profile: action.profile.name,
        parameters: Object.fromEntries(
          action.profile.parameters.map((parameter) => [
            parameter.name,
            data.parameters[parameter.name] ?? parameter.min,
          ]),
        ),
      }));
    case "parameterChanged":
      // undefined means the field is empty while the user types.
      return edit(state, action.key, (data) => {
        const parameters = { ...data.parameters };
        if (action.value === undefined) {
          delete parameters[action.name];
        } else {
          parameters[action.name] = action.value;
        }
        return { ...data, parameters };
      });
    case "removed": {
      const nodes = state.nodes.filter((node) => node.id !== action.key);
      return {
        ...state,
        dirty: true,
        nodes,
        edges: withoutDangling(state.edges, nodes),
        workload: withoutMissingTarget(state.workload, nodes),
      };
    }
    case "workloadAdded":
      // The user picks the target next; a copy of the initial values, so the profile never changes.
      return {
        ...state,
        dirty: true,
        workload: {
          targetKey: null,
          profile: action.profile.name,
          parameters: { ...action.profile.initialParameters },
          seed: action.seed,
        },
      };
    case "workloadProfileChanged":
      // Keeps the values the new profile shares; new parameters start at their initial value.
      return editWorkload(state, (workload) => ({
        ...workload,
        profile: action.profile.name,
        parameters: Object.fromEntries(
          action.profile.parameters.map((parameter) => [
            parameter.name,
            workload.parameters[parameter.name] ??
              action.profile.initialParameters[parameter.name] ??
              parameter.min,
          ]),
        ),
      }));
    case "workloadTargetChanged":
      return editWorkload(state, (workload) => ({
        ...workload,
        targetKey: action.targetKey,
      }));
    case "workloadParameterChanged":
      // undefined means the field is empty while the user types.
      return editWorkload(state, (workload) => {
        const parameters = { ...workload.parameters };
        if (action.value === undefined) {
          delete parameters[action.name];
        } else {
          parameters[action.name] = action.value;
        }
        return { ...workload, parameters };
      });
    case "workloadSeedChanged":
      return editWorkload(state, (workload) => ({
        ...workload,
        seed: action.seed,
      }));
    case "workloadRemoved":
      return state.workload ? { ...state, dirty: true, workload: null } : state;
    case "edgesChanged":
      // React Flow reports selections and deletions of arrows. Selecting is not a change to save.
      return {
        ...state,
        dirty:
          state.dirty ||
          action.changes.some((change) => change.type === "remove"),
        edges: applyEdgeChanges(action.changes, state.edges),
      };
    case "connected": {
      // The canvas only offers compatible targets; a repeat or a loop onto the same node is ignored.
      const { connection } = action;
      if (
        connection.sourceKey === connection.targetKey ||
        state.edges.some((edge) => edge.id === connectionId(connection))
      ) {
        return state;
      }
      return {
        ...state,
        dirty: true,
        edges: [...state.edges, toEdge(connection)],
      };
    }
    case "disconnected":
      return {
        ...state,
        dirty: true,
        edges: state.edges.filter((edge) => edge.id !== action.id),
      };
    case "saveStarted":
      // What was sent is no longer a local change.
      return { ...state, status: "saving", dirty: false, error: null };
    case "saved": {
      const { graph } = action;
      // The user edited while saving: keep those edits and only take the new revision and issues.
      if (state.dirty) {
        return {
          ...state,
          revision: graph.revision,
          issues: graph.issues,
          status: "idle",
        };
      }
      // Otherwise take the saved graph (the backend trims names), keeping the selection.
      const selected = new Set(
        [...state.nodes, ...state.edges]
          .filter((element) => element.selected)
          .map((element) => element.id),
      );
      return {
        ...toCanvasState(graph),
        nodes: graph.components.map((component) =>
          toNode(component, selected.has(component.key)),
        ),
        edges: graph.connections.map((connection) =>
          toEdge(connection, selected.has(connectionId(connection))),
        ),
      };
    }
    // The graph stays on screen so it can be saved again.
    case "saveFailed":
      return { ...state, status: "failed", dirty: true, error: action.message };
    // Another tab saved first; the editor offers to reload or to save over it.
    case "conflicted":
      return {
        ...state,
        status: "conflict",
        dirty: true,
        error: action.message,
      };
    // The session ended: the work stays on screen and in the local draft until the user signs in again.
    case "expired":
      return { ...state, status: "expired", dirty: true, error: null };
    // "Discard mine and reload": the saved graph replaces the local one.
    case "reloaded":
      return toCanvasState(action.graph);
    // Unsaved work recovered from the browser. It keeps the revision it started from, so if the
    // scenario was saved since then, the next save gets the usual conflict instead of overwriting it.
    case "restored":
      return {
        ...state,
        revision: action.draft.baseRevision,
        nodes: action.draft.components.map((component) => toNode(component)),
        // Drafts saved before connections existed have none.
        edges: (action.draft.connections ?? []).map((connection) =>
          toEdge(connection),
        ),
        // Drafts saved before loads existed keep the load on screen.
        workload:
          action.draft.workload === undefined
            ? state.workload
            : action.draft.workload,
        dirty: true,
        status: "idle",
        error: null,
      };
  }
}
