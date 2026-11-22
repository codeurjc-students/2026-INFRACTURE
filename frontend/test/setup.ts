import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";
import { FakeEventSource } from "./event-source-mock";

afterEach(() => {
  cleanup();
  FakeEventSource.reset();
});
