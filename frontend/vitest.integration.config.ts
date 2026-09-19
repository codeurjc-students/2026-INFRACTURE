import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    environment: "jsdom",
    environmentOptions: {
      jsdom: {
        url: "http://127.0.0.1:8081",
      },
    },
    include: ["test/integration/**/*.test.tsx"],
    setupFiles: "./test/setup.ts",
  },
});
