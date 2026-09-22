import { configDefaults, defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    coverage: {
      // Generated React Router files live outside app/, so no exclusions are needed.
      include: ["app/**/*.{ts,tsx}"],
      provider: "v8",
      reporter: ["text", "html", ["lcov", { projectRoot: ".." }]],
      thresholds: {
        lines: 70,
      },
    },
    environment: "jsdom",
    exclude: [
      ...configDefaults.exclude,
      "test/integration/**",
      "test/system/**",
    ],
    setupFiles: "./test/setup.ts",
  },
});
