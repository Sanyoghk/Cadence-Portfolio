import { defineConfig } from "vite";

export default defineConfig({
  build: {
    outDir: "dist/client",
  },
  optimizeDeps: {
    include: ["react", "react-dom/client"],
  },
  server: {
    host: "127.0.0.1",
    allowedHosts: ["localhost", "127.0.0.1"],
    warmup: {
      clientFiles: ["./src/main.jsx"],
    },
  },
});
