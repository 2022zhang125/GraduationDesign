import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const apiOrigin = process.env.VITE_API_ORIGIN || "https://ncf.back.believesun.cn";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: apiOrigin,
        changeOrigin: true,
        secure: true
      }
    }
  }
});
