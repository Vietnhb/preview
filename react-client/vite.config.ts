import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: { proxy: { '/api': {
    target: loadEnv(mode, process.cwd(), 'VITE_').VITE_API_PROXY_TARGET || 'http://localhost:8080',
    changeOrigin: true,
  } } },
}))
