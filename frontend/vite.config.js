import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: false,
  },
  preview: {
    host: 'localhost',
    port: 4173,
    strictPort: false,
  },
})
