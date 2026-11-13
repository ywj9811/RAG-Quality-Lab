/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 개발 서버 포트와 API 프록시 대상은 ADR-0008 포트 표를 따른다.
const apiTarget = process.env.RQL_API_PROXY_TARGET ?? "http://127.0.0.1:18080";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 15173,
    strictPort: true,
    proxy: {
      "/api": apiTarget,
      "/actuator": apiTarget,
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/setupTests.ts"],
  },
});
