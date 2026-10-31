import "@xyflow/react/dist/style.css";
import {
  Background,
  BackgroundVariant,
  ControlButton,
  Controls,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  useStore,
  type FitViewOptions,
  type NodeChange,
} from "@xyflow/react";
import {
  CheckIcon,
  CircleAlertIcon,
  InfoIcon,
  MaximizeIcon,
  SaveIcon,
  TriangleAlertIcon,
} from "lucide-react";
import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
  type CSSProperties,
  type DragEvent,
} from "react";
import { Link } from "react-router";
import Logo from "~/components/logo";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { Spinner } from "~/components/ui/spinner";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import type { ProjectDTO } from "~/features/projects/model/ProjectDTO";
import type { ScenarioDTO } from "~/features/projects/model/ScenarioDTO";
import { HttpError } from "~/shared/api/api-fetch";
import { getGraph, saveGraph } from "../api/graph-service";
import {
  BOARD,
  graphReducer,
  MAX_COMPONENTS,
  toCanvasState,
  toSaveGraph,
  type CanvasState,
  type ComponentNode as ComponentNodeModel,
} from "../graph-reducer";
import type { ScenarioGraphDTO } from "../model/ScenarioGraphDTO";
import ComponentNode, { type DisplayedNode } from "./component-node";
import ComponentPalette, { TEMPLATE_MIME } from "./component-palette";
import NodeInspector from "./node-inspector";
import ScenarioDetails from "./scenario-details";

// Only to show "⌘S" or "Ctrl S" in the save button tooltip.
const isMac =
  typeof navigator !== "undefined" &&
  /Mac|iPhone|iPad/.test(navigator.platform);
// Shown when an error does not come from the backend (for example, no network).
const GENERIC_ERROR = "Something went wrong. Try again in a moment.";
// Tells React Flow to draw nodes of type "component" with ComponentNode.
// Defined outside the component so React Flow does not get a new object on every render.
const nodeTypes = { component: ComponentNode };
// How the view frames the nodes: never zoomed in past 100%, and leaving the bottom band
// free for the minimap on screens wider than a phone.
const FIT_VIEW: FitViewOptions = {
  maxZoom: 1,
  padding:
    typeof window !== "undefined" &&
    window.matchMedia?.("(min-width: 640px)").matches
      ? { top: "48px", left: "64px", right: "48px", bottom: "190px" }
      : "24px",
};
// One area for both panning and dragging: whatever is visible is somewhere a node can go.
const BOARD_EXTENT: [[number, number], [number, number]] = [
  [0, 0],
  [BOARD.width, BOARD.height],
];
// Style of the links in the breadcrumb.
const crumbLink =
  "active:text-foreground pointer-fine:hover:text-foreground motion-safe:transition-colors";

// React Flow draws its own chrome; its variables take the app's tokens so both themes follow DESIGN.md.
const flowTheme = {
  "--xy-background-color": "var(--background)",
  "--xy-background-pattern-color":
    "color-mix(in oklch, var(--foreground) 18%, transparent)",
  "--xy-controls-button-background-color": "var(--card)",
  "--xy-controls-button-background-color-hover": "var(--accent)",
  "--xy-controls-button-color": "var(--foreground)",
  "--xy-controls-button-border-color": "var(--border)",
  "--xy-node-boxshadow-selected": "none",
  "--xy-attribution-background-color": "transparent",
  "--xy-minimap-background-color": "var(--card)",
  "--xy-minimap-mask-background-color":
    "color-mix(in oklch, var(--background) 70%, transparent)",
  "--xy-minimap-mask-stroke-color": "var(--foreground)",
  "--xy-minimap-node-stroke-color": "var(--input)",
} as CSSProperties;

// Everything the route loaded.
interface ScenarioCanvasProps {
  project: ProjectDTO;
  scenario: ScenarioDTO;
  graph: ScenarioGraphDTO;
  templates: ComponentTemplateDTO[];
}

// The backend explains its errors in the ProblemDetail "detail"; anything else gets a generic message.
function errorMessage(error: unknown) {
  return error instanceof HttpError ? error.message : GENERIC_ERROR;
}

// Announces the save state to screen readers. It is hidden on screen because the save button already shows it.
function SaveStatus({ state }: { state: CanvasState }) {
  const text =
    state.status === "saving"
      ? "Saving…"
      : state.dirty
        ? "Unsaved changes"
        : "Graph saved";
  return (
    <p role="status" className="sr-only">
      <span>{text}</span> <span>{`Revision ${state.revision}`}</span>
    </p>
  );
}

// The whole scenario editor: top bar, component list, canvas and side panel.
function Editor({ project, scenario, graph, templates }: ScenarioCanvasProps) {
  // The graph state lives in the reducer; every change goes through dispatch.
  const [state, dispatch] = useReducer(graphReducer, graph, toCanvasState);
  // React Flow helpers: screen-to-board coordinates, framing and reading or moving the view.
  const { screenToFlowPosition, fitView, getViewport, setViewport } =
    useReactFlow();
  // Current size of the canvas in pixels.
  const width = useStore((store) => store.width);
  const height = useStore((store) => store.height);
  // Zooming out stops when the board fills the canvas, so nothing outside it is ever shown.
  const minZoom =
    width && height
      ? Math.max(width / BOARD.width, height / BOARD.height)
      : 0.25;

  // React Flow's fitView and a canvas resize ignore translateExtent; move the view back onto the board.
  const keepOnBoard = useCallback(() => {
    if (!width || !height) return;
    const viewport = getViewport();
    const zoom = Math.max(viewport.zoom, minZoom);
    setViewport({
      zoom,
      x: Math.min(0, Math.max(width - BOARD.width * zoom, viewport.x)),
      y: Math.min(0, Math.max(height - BOARD.height * zoom, viewport.y)),
    });
  }, [width, height, minZoom, getViewport, setViewport]);
  useEffect(keepOnBoard, [keepOnBoard]);

  // Frames the nodes and then makes sure the view is still on the board.
  async function fitToBoard(options?: FitViewOptions) {
    await fitView({ ...FIT_VIEW, ...options });
    keepOnBoard();
  }
  // Screen state that is not part of the graph.
  const area = useRef<HTMLDivElement>(null);
  const [resolving, setResolving] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);
  // Templates by key, to find a node's template quickly.
  const templatesByKey = useMemo(
    () => new Map(templates.map((template) => [template.key, template])),
    [templates],
  );
  const full = state.nodes.length >= MAX_COMPONENTS;
  const saving = state.status === "saving" || resolving;
  const projectId = String(project.id);
  const scenarioId = String(scenario.id);

  // The nodes as they are drawn: each one gets its template name and its issues.
  // Only for drawing; the state keeps the data as the backend sends it.
  const displayed = useMemo<DisplayedNode[]>(() => {
    const byNode = new Map<string, string[]>();
    for (const issue of state.issues) {
      if (issue.target.kind === "COMPONENT" && issue.target.key) {
        const fields = byNode.get(issue.target.key) ?? [];
        fields.push(issue.field ?? "");
        byNode.set(issue.target.key, fields);
      }
    }
    return state.nodes.map((node) => {
      const templateName =
        templatesByKey.get(node.data.templateKey)?.name ?? node.data.type;
      const issueFields = byNode.get(node.id) ?? [];
      return {
        ...node,
        ariaLabel: `${node.data.name}, ${templateName}`,
        data: {
          ...node.data,
          templateName,
          issueCount: issueFields.length,
          issueFields,
        },
      };
    });
  }, [state.nodes, state.issues, templatesByKey]);

  // The side panel shows the selected node, or the scenario details.
  const selected = state.nodes.find((node) => node.selected);
  const panelOpen = Boolean(selected) || detailsOpen;

  // Selecting a component replaces the details in the side panel.
  useEffect(() => {
    if (selected) setDetailsOpen(false);
  }, [selected]);

  // Selects one node, or none with null, the same way a click on the canvas does.
  function select(key: string | null) {
    dispatch({
      type: "nodesChanged",
      changes: state.nodes.map((node) => ({
        type: "select",
        id: node.id,
        selected: node.id === key,
      })),
    });
  }

  // The issues button in the top bar: selects the first node with an issue and centres the view on it.
  function showFirstIssue() {
    const key = state.issues.find(
      (issue) => issue.target.kind === "COMPONENT" && issue.target.key,
    )?.target.key;
    if (!key) return;
    select(key);
    fitToBoard({ nodes: [{ id: key }], padding: "24px" });
  }

  // Adds a node from a template: in the middle of the canvas, or where it was dropped.
  function add(
    template: ComponentTemplateDTO,
    point?: { x: number; y: number },
  ) {
    const rect = area.current!.getBoundingClientRect();
    const position = screenToFlowPosition(
      point ?? {
        x: rect.left + rect.width / 2 - 88,
        y: rect.top + rect.height / 2 - 40,
      },
    );
    dispatch({
      type: "added",
      key: crypto.randomUUID(),
      template,
      position: { x: Math.round(position.x), y: Math.round(position.y) },
    });
  }

  // A template dropped from the component list: the list put its key in the drag data.
  function drop(event: DragEvent) {
    const template = templatesByKey.get(
      event.dataTransfer.getData(TEMPLATE_MIME),
    );
    setDragging(false);
    if (!template || full) return;
    event.preventDefault();
    add(template, { x: event.clientX, y: event.clientY });
  }

  // Sends the whole graph with the expected revision. A 409 means another tab saved first.
  async function save(revision = state.revision) {
    dispatch({ type: "saveStarted" });
    try {
      const saved = await saveGraph(
        projectId,
        scenarioId,
        toSaveGraph(state.nodes, revision),
      );
      dispatch({ type: "saved", graph: saved });
    } catch (error) {
      if (error instanceof HttpError && error.status === 409) {
        dispatch({ type: "conflicted", message: error.message });
      } else {
        dispatch({ type: "saveFailed", message: errorMessage(error) });
      }
    }
  }

  const canSave = state.dirty && !saving && state.status !== "conflict";
  // ⌘S / Ctrl S saves. The ref always points to the current save, so the listener is added only once.
  const saveShortcut = useRef<() => void>(() => {});
  saveShortcut.current = () => {
    if (canSave) save();
  };
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        saveShortcut.current();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  // After a conflict: reload the saved graph, or save the local one over it with the new revision.
  async function resolve(keepMine: boolean) {
    setResolving(true);
    try {
      const latest = await getGraph(projectId, scenarioId);
      if (keepMine) {
        await save(latest.revision);
      } else {
        dispatch({ type: "reloaded", graph: latest });
      }
    } catch (error) {
      dispatch({ type: "saveFailed", message: errorMessage(error) });
    } finally {
      setResolving(false);
    }
  }

  return (
    <div className="flex min-h-svh flex-col bg-background lg:h-svh">
      {/* Top bar: logo, breadcrumb, issues, details and save. */}
      <header className="flex h-14 shrink-0 items-center gap-2 border-b px-2 sm:gap-3 sm:px-3">
        <Link
          to="/"
          aria-label="Infracture home"
          className="grid size-9 shrink-0 place-items-center rounded-md text-foreground active:scale-[0.96] pointer-fine:hover:bg-accent motion-safe:transition-[background-color,scale] motion-safe:duration-150"
        >
          <Logo markOnly className="h-[18px] w-auto" />
        </Link>
        <nav aria-label="Breadcrumb" className="min-w-0 flex-1">
          <ol className="flex min-w-0 items-center gap-1.5 text-sm max-sm:flex-col max-sm:items-start max-sm:gap-0">
            <li className="max-w-full min-w-12 truncate text-muted-foreground max-sm:text-xs">
              <Link to={`/projects/${project.id}`} className={crumbLink}>
                {project.name}
              </Link>
            </li>
            <li
              aria-hidden="true"
              className="text-muted-foreground/60 max-sm:hidden"
            >
              /
            </li>
            <li aria-current="page" className="max-w-full min-w-0 shrink-[0.1]">
              <h1
                title={scenario.name}
                className="truncate font-display text-[15px] leading-5 font-semibold"
              >
                {scenario.name}
              </h1>
            </li>
          </ol>
        </nav>
        {state.issues.length > 0 && (
          <Button
            type="button"
            variant="ghost"
            onClick={showFirstIssue}
            aria-label={
              state.issues.length === 1
                ? "1 issue"
                : `${state.issues.length} issues`
            }
            title="Show the first issue"
            className="font-mono text-degraded-text tabular-nums pointer-fine:hover:text-degraded-text"
          >
            <TriangleAlertIcon data-icon="inline-start" />
            {state.issues.length}
          </Button>
        )}
        <Button
          type="button"
          variant="ghost"
          aria-pressed={detailsOpen && !selected}
          onClick={() => {
            if (selected) select(null);
            setDetailsOpen(!(detailsOpen && !selected));
          }}
          aria-label="Details"
          title="Scenario details"
          size="icon"
          className="text-muted-foreground aria-pressed:bg-accent aria-pressed:text-foreground dark:aria-pressed:bg-accent"
        >
          <InfoIcon />
        </Button>
        <SaveStatus state={state} />
        <Button
          type="button"
          onClick={() => save()}
          disabled={!canSave}
          aria-keyshortcuts="Meta+S Control+S"
          title={`Save the graph (${isMac ? "⌘S" : "Ctrl S"})`}
          className="min-w-20 px-3"
        >
          {saving ? (
            <Spinner data-icon="inline-start" />
          ) : state.dirty ? (
            <SaveIcon data-icon="inline-start" />
          ) : (
            <CheckIcon data-icon="inline-start" />
          )}
          {saving ? "Saving…" : state.dirty ? "Save" : "Saved"}
        </Button>
      </header>

      <div
        className={`grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)] max-lg:content-start ${
          panelOpen
            ? "lg:grid-cols-[15rem_minmax(0,1fr)_20rem]"
            : "lg:grid-cols-[15rem_minmax(0,1fr)]"
        }`}
      >
        {/* Left: the component list. */}
        <aside className="flex min-h-0 flex-col border-b lg:border-r lg:border-b-0">
          <ComponentPalette
            templates={templates}
            count={state.nodes.length}
            onAdd={(template) => add(template)}
          />
        </aside>

        {/* Centre: the canvas. The wrapper receives templates dragged from the list. */}
        <div
          ref={area}
          onDragOver={(event) => {
            if (event.dataTransfer.types.includes(TEMPLATE_MIME)) {
              event.preventDefault();
              event.dataTransfer.dropEffect = "copy";
              if (!dragging) setDragging(true);
            }
          }}
          onDragLeave={(event) => {
            if (
              !event.currentTarget.contains(event.relatedTarget as Node | null)
            ) {
              setDragging(false);
            }
          }}
          onDrop={drop}
          className="relative min-h-0 max-lg:h-[60svh]"
        >
          {/* Controlled React Flow: it reports moves, selections and deletions through onNodesChange,
              and the reducer decides. The extents and zoom limits keep everything on the board. */}
          <ReactFlow
            nodes={displayed}
            nodeTypes={nodeTypes}
            onNodesChange={(changes) =>
              dispatch({
                type: "nodesChanged",
                changes: changes as NodeChange<ComponentNodeModel>[],
              })
            }
            deleteKeyCode={["Backspace", "Delete"]}
            nodeExtent={BOARD_EXTENT}
            translateExtent={BOARD_EXTENT}
            minZoom={minZoom}
            maxZoom={1.5}
            attributionPosition="top-right"
            onInit={() => fitToBoard()}
            style={flowTheme}
            aria-label="Scenario graph"
          >
            <Background variant={BackgroundVariant.Dots} gap={20} size={1.2} />
            <Controls
              showInteractive={false}
              showFitView={false}
              position="bottom-left"
            >
              <ControlButton
                onClick={() => fitToBoard()}
                title="Fit view"
                aria-label="Fit view"
              >
                <MaximizeIcon style={{ fill: "none" }} />
              </ControlButton>
            </Controls>
            <MiniMap
              pannable
              zoomable
              position="bottom-right"
              ariaLabel="Mini map"
              style={{ width: 120, height: 80 }}
              nodeBorderRadius={12}
              nodeColor={(node) =>
                (node as DisplayedNode).data.issueCount > 0
                  ? "var(--degraded-fill)"
                  : "var(--accent)"
              }
              className={`rounded-lg border max-sm:hidden ${
                state.nodes.length === 0 ? "hidden" : ""
              }`}
            />
          </ReactFlow>

          {/* Overlays: drop zone, empty state, and save errors or conflicts. */}
          {dragging && (
            <div className="pointer-events-none absolute inset-2 grid place-items-center rounded-xl border-2 border-dashed border-foreground/35 bg-foreground/[0.03]">
              <p className="rounded-md bg-card px-3 py-1.5 text-sm font-medium">
                Drop to add it here
              </p>
            </div>
          )}

          {state.nodes.length === 0 && !dragging && (
            <div className="pointer-events-none absolute inset-0 grid place-items-center p-6">
              <div className="flex max-w-80 flex-col items-center gap-1.5 rounded-xl border border-dashed border-input bg-background/80 px-6 py-5 text-center">
                <h2 className="text-sm font-semibold">No components yet</h2>
                <p className="type-small text-muted-foreground">
                  Click a component in the list, or drag it here.
                </p>
              </div>
            </div>
          )}

          {(state.status === "failed" || state.status === "conflict") && (
            <div className="absolute inset-x-3 top-3 z-10 mx-auto max-w-xl">
              <Alert
                variant="destructive"
                role="alert"
                className="bg-card shadow-lg"
              >
                <CircleAlertIcon />
                <AlertTitle>
                  {state.status === "conflict"
                    ? "This scenario was saved somewhere else"
                    : "Could not save the graph"}
                </AlertTitle>
                <AlertDescription className="flex flex-col gap-3">
                  <span>{`${state.error} Your changes are still here.`}</span>
                  {state.status === "conflict" && (
                    <span className="flex flex-wrap gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        disabled={resolving}
                        onClick={() => resolve(false)}
                        className="text-foreground"
                      >
                        Discard mine and reload
                      </Button>
                      <Button
                        type="button"
                        disabled={resolving}
                        onClick={() => resolve(true)}
                      >
                        Save mine over it
                      </Button>
                    </span>
                  )}
                </AlertDescription>
              </Alert>
            </div>
          )}
        </div>

        {/* Right: the selected node's configuration, or the scenario details. */}
        {panelOpen && (
          <aside
            aria-label="Inspector"
            className="min-h-0 overflow-y-auto border-t p-4 lg:border-t-0 lg:border-l lg:p-5"
          >
            {selected ? (
              <NodeInspector
                key={selected.id}
                node={selected}
                template={templatesByKey.get(selected.data.templateKey)}
                issues={state.issues.filter(
                  (issue) =>
                    issue.target.kind === "COMPONENT" &&
                    issue.target.key === selected.id,
                )}
                dispatch={dispatch}
                onClose={() => select(null)}
              />
            ) : (
              <ScenarioDetails
                scenario={scenario}
                revision={state.revision}
                onClose={() => setDetailsOpen(false)}
              />
            )}
          </aside>
        )}
      </div>
    </div>
  );
}

// React Flow's helpers (useReactFlow, useStore) only work inside its provider.
export default function ScenarioCanvas(props: ScenarioCanvasProps) {
  return (
    <ReactFlowProvider>
      <Editor {...props} />
    </ReactFlowProvider>
  );
}
