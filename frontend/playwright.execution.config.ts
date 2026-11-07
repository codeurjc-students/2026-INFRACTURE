import { defineConfig, devices } from "@playwright/test";

// The experiment test runs against the backend in a container, the way Infracture is installed,
// because only there the backend joins the execution network and sees the service's response.
// It starts its own Compose project, so it cannot run beside a development backend on port 8080.
export default defineConfig({
  testDir: "./test/execution",
  forbidOnly: Boolean(process.env.CI),
  workers: 1,
  timeout: 300_000,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report/execution" }],
  ],
  outputDir: "test-results/execution",
  use: {
    baseURL: "http://127.0.0.1:5175",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: [
    {
      command: "./scripts/test-execution-stack.sh",
      cwd: "..",
      name: "Containerized platform",
      url: "http://127.0.0.1:8080/actuator/health",
      reuseExistingServer: false,
      // Building the backend and demo images takes a few minutes on a clean machine.
      timeout: 900_000,
      gracefulShutdown: { signal: "SIGTERM", timeout: 60_000 },
    },
    {
      command: "npm run dev -- --host 127.0.0.1 --port 5175 --strictPort",
      env: { VITE_API_BASE_URL: "http://127.0.0.1:8080" },
      name: "Test frontend",
      url: "http://127.0.0.1:5175",
      reuseExistingServer: false,
      timeout: 120_000,
      gracefulShutdown: { signal: "SIGTERM", timeout: 10_000 },
    },
  ],
});
