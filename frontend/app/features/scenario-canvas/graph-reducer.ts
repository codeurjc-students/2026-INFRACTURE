import { applyNodeChanges, type Node, type NodeChange } from "@xyflow/react";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import type { TemplateContractDTO } from "~/features/component-catalogue/model/TemplateContractDTO";
import type { ComponentDTO } from "./model/ComponentDTO";
import type { PositionDTO } from "./model/PositionDTO";
import type { SaveScenarioGraphDTO } from "./model/SaveScenarioGraphDTO";
import type { ScenarioGraphDTO } from "./model/ScenarioGraphDTO";
import type { ValidationIssueDTO } from "./model/ValidationIssueDTO";

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
  // Contract issues from the last read or save.
  issues: ValidationIssueDTO[];
  // Local changes which have not been saved to the server yet.
  dirty: boolean;
  // Drives the save button and the error and conflict alerts.
  status: "idle" | "saving" | "failed" | "conflict";
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
  | { type: "saveStarted" }
  | { type: "saved"; graph: ScenarioGraphDTO }
  | { type: "saveFailed"; message: string }
  | { type: "conflicted"; message: string }
  | { type: "reloaded"; graph: ScenarioGraphDTO };

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
    issues: graph.issues,
    dirty: false,
    status: "idle",
    error: null,
  };
}

// Builds the body of the save request from the nodes: the backend accepts neither the type nor the selection.
export function toSaveGraph(
  nodes: ComponentNode[],
  revision: number,
): SaveScenarioGraphDTO {
  return {
    revision,
    components: nodes.map(({ id, position, data }) => ({
      key: id,
      templateKey: data.templateKey,
      name: data.name,
      position: { x: position.x, y: position.y },
      profile: data.profile,
      parameters: data.parameters,
    })),
  };
}

// Picks a free name: "HTTP Service", then "HTTP Service 2", "HTTP Service 3"...
function nextName(nodes: ComponentNode[], base: string) {
  const names = new Set(nodes.map((node) => node.data.name));
  let name = base;
  for (let index = 2; names.has(name); index++) {
    name = `${base} ${index}`;
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
      return {
        ...state,
        dirty: state.dirty || edited,
        nodes: applyNodeChanges(action.changes, state.nodes),
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
    case "removed":
      return {
        ...state,
        dirty: true,
        nodes: state.nodes.filter((node) => node.id !== action.key),
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
        state.nodes.filter((node) => node.selected).map((node) => node.id),
      );
      return {
        ...toCanvasState(graph),
        nodes: graph.components.map((component) =>
          toNode(component, selected.has(component.key)),
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
    // "Discard mine and reload": the saved graph replaces the local one.
    case "reloaded":
      return toCanvasState(action.graph);
  }
}
