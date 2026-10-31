import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");

  return {
    plugins: [tailwindcss(), reactRouter()],
    resolve: {
      tsconfigPaths: true,
    },
    // Scan every module at startup: dependencies found later, when a route first loads, make Vite reload
    // mid-navigation and briefly show the error page with two copies of React.
    optimizeDeps: {
      entries: ["./app/**/*.{ts,tsx}"],
    },
    server: {
      watch: { ignored: ["**/coverage/**", "**/build/**"] },
      proxy: {
        "/api": {
          target: env.VITE_API_BASE_URL || "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
