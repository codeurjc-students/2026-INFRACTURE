import { afterEach, describe, expect, it } from "vitest";

import {
  clearDraft,
  draftKey,
  readDraft,
  writeDraft,
  type GraphDraft,
} from "~/features/scenario-canvas/draft-storage";

const draft: GraphDraft = {
  baseRevision: 3,
  components: [],
  savedAt: "2026-10-31T10:00:00Z",
};

afterEach(() => localStorage.clear());

describe("draft storage", () => {
  it("keeps one draft per account and scenario", () => {
    writeDraft(draftKey(5, 3), draft);

    expect(readDraft(draftKey(5, 3))).toEqual(draft);
    expect(readDraft(draftKey(6, 3))).toBeNull();
    expect(readDraft(draftKey(5, 4))).toBeNull();

    clearDraft(draftKey(5, 3));
    expect(readDraft(draftKey(5, 3))).toBeNull();
  });

  it("ignores anything stored that is not a draft", () => {
    localStorage.setItem(draftKey(5, 3), "{not json");
    expect(readDraft(draftKey(5, 3))).toBeNull();

    localStorage.setItem(
      draftKey(5, 3),
      JSON.stringify({ baseRevision: "3", components: [] }),
    );
    expect(readDraft(draftKey(5, 3))).toBeNull();
  });
});
