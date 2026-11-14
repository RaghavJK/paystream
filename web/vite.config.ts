import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The dev server proxies API calls, so the browser never needs CORS in local development.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api/payments": "http://localhost:8081",
      "/api/risk": "http://localhost:8082",
      "/agent": "http://localhost:8083",
    },
  },
});
