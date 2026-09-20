import { configDefaults, defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    environment: "jsdom",
    exclude: [
      ...configDefaults.exclude,
      "test/integration/**",
      "test/system/**",
    ],
    setupFiles: "./test/setup.ts",
  },
});
