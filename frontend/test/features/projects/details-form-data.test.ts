import { describe, expect, it } from "vitest";
import { formatUpdated } from "~/features/projects/details-form-data";

const now = Date.parse("2026-10-20T15:00:00Z");

describe("formatUpdated", () => {
  it("reads recent changes as relative time", () => {
    expect(formatUpdated("2026-10-20T14:59:40Z", now)).toBe("just now");
    expect(formatUpdated("2026-10-20T14:58:00Z", now)).toBe("2 minutes ago");
    expect(formatUpdated("2026-10-20T12:00:00Z", now)).toBe("3 hours ago");
    expect(formatUpdated("2026-10-19T15:00:00Z", now)).toBe("yesterday");
  });

  it("shows the date for changes older than a week", () => {
    expect(formatUpdated("2026-10-10T15:00:00Z", now)).toBe("10 Oct 2026");
  });
});
