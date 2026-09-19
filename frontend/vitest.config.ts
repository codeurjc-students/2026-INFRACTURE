import { configDefaults, defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    environment: "jsdom",
    exclude: [...configDefaults.exclude, "test/integration/**"],
    setupFiles: "./test/setup.ts",
  },
});
