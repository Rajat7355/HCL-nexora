import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// /api is proxied to the Spring Boot backend (8080). In "mock" mode it goes to the local mock server (8090).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = mode === 'mock' ? 'http://localhost:8090' : env.VITE_API_TARGET || 'http://localhost:8080'
  return {
    plugins: [react(), tailwindcss()],
    server: { port: 5173, proxy: { '/api': { target, changeOrigin: true } } },
    test: { environment: 'node' },
  }
})
