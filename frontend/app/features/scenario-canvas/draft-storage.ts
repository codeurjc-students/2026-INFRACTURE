import { useEffect, useState } from "react";
import { toComponents, type CanvasState } from "./graph-reducer";
import type { ComponentDTO } from "./model/ComponentDTO";

// Unsaved work kept in this browser so it survives a reload, a discarded conflict or an expired session.
// It holds only what the user typed on the canvas (names, positions and parameters), nothing secret.
export interface GraphDraft {
  // The revision the work started from; restoring it keeps the conflict check.
  baseRevision: number;
  components: ComponentDTO[];
  savedAt: string;
}

// One draft per account and scenario, so another account on the same browser never sees it.
export function draftKey(userId: number, scenarioId: number) {
  return `infracture:scenario-draft:${userId}:${scenarioId}`;
}

// Storage can be full, blocked or edited by hand: any problem simply means there is no draft.
export function readDraft(key: string): GraphDraft | null {
  try {
    const draft = JSON.parse(localStorage.getItem(key) ?? "null");
    return Number.isInteger(draft?.baseRevision) &&
      Array.isArray(draft?.components)
      ? draft
      : null;
  } catch {
    return null;
  }
}

export function writeDraft(key: string, draft: GraphDraft) {
  try {
    localStorage.setItem(key, JSON.stringify(draft));
  } catch {
    // Without storage the editor still works; only the recovery is lost.
  }
}

export function clearDraft(key: string) {
  try {
    localStorage.removeItem(key);
  } catch {
    // Nothing to clear when storage is not available.
  }
}

// Keeps the browser copy in step with the editor. A draft found on opening is offered first and
// nothing is written over it until the user restores or discards it.
export function useGraphDraft(key: string | null, state: CanvasState) {
  const [pending, setPending] = useState<GraphDraft | null>(() =>
    key ? readDraft(key) : null,
  );

  useEffect(() => {
    if (!key || pending) return;
    if (state.dirty) {
      writeDraft(key, {
        baseRevision: state.revision,
        components: toComponents(state.nodes),
        savedAt: new Date().toISOString(),
      });
    } else if (state.status === "idle") {
      // Only once the server confirmed the save: a save in flight can still fail.
      clearDraft(key);
    }
  }, [key, pending, state.dirty, state.status, state.nodes, state.revision]);

  return {
    pending,
    // The pending draft was handed to the editor; stop offering it.
    take: () => setPending(null),
    discard: () => {
      if (key) clearDraft(key);
      setPending(null);
    },
    // Keeps a version the user just discarded so it can be offered back.
    keep: (draft: GraphDraft) => {
      if (!key) return;
      writeDraft(key, draft);
      setPending(draft);
    },
  };
}
